package ru.mirea.avia.service;

import ru.mirea.avia.exception.BusinessException;
import ru.mirea.avia.exception.EntityNotFoundException;
import ru.mirea.avia.model.Booking;
import ru.mirea.avia.model.BookingStatus;
import ru.mirea.avia.model.FareClass;
import ru.mirea.avia.model.Flight;
import ru.mirea.avia.model.Passenger;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.FlightRepository;
import ru.mirea.avia.repository.PassengerRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Прикладная логика бронирований — основной сущности системы. */
public class BookingService {
    private static final Pattern SEAT = Pattern.compile("^[0-9]{1,2}[A-F]$");
    private static final Duration CANCEL_LIMIT = Duration.ofHours(2);
    private static final Duration EXPIRE_AFTER = Duration.ofMinutes(30);

    private final BookingRepository bookings;
    private final PassengerRepository passengers;
    private final FlightRepository flights;

    public BookingService(BookingRepository bookings, PassengerRepository passengers, FlightRepository flights) {
        this.bookings = bookings;
        this.passengers = passengers;
        this.flights = flights;
    }

    public Booking create(long passengerId, long flightId, String seatNumber, FareClass fareClass,
                        BigDecimal price) throws SQLException {
        // Перед созданием брони просроченные аннулируются, их места снова свободны.
        expireOutdated();
        Passenger passenger = requirePassenger(passengerId);
        Flight flight = requireFlight(flightId);
        String seat = normalizeSeat(seatNumber);
        checkOpenForSale(flight);
        checkSeatFree(flight, seat, null);
        checkNoDuplicateBooking(passenger, flight);
        checkPrice(price);
        Booking booking = new Booking(passenger, flight, seat, fareClass, price);
        return bookings.create(booking);
    }

    /** Все брони; перед выводом просроченные аннулируются, чтобы статусы были актуальны. */
    public List<Booking> list() throws SQLException {
        expireOutdated();
        return bookings.findAll();
    }

    /** Бронь по ID; бронь, которая к этому моменту просрочена, уже будет в статусе EXPIRED. */
    public Booking get(long id) throws SQLException {
        expireOutdated();
        return bookings.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Бронирование с ID " + id + " не найдено"));
    }

    /** Смена места, класса и стоимости с повторной проверкой занятости места. */
    public Booking update(long id, String seatNumber, FareClass fareClass, BigDecimal price) throws SQLException {
        // Правила проверяются заново: между вводом и сохранением данные могли измениться.
        Booking booking = requireEditable(id);
        String seat = validateSeatChange(booking, seatNumber);
        checkPrice(price);
        booking.setSeatNumber(seat);
        booking.setFareClass(fareClass);
        booking.setPrice(price);
        bookings.update(booking);
        return booking;
    }

    /** Возвращает бронь, если её можно изменить: статус CREATED или PAID и рейс ещё открыт для продажи. */
    public Booking requireEditable(long id) throws SQLException {
        Booking booking = get(id);
        BookingStatus status = booking.getStatus();
        if (status != BookingStatus.CREATED && status != BookingStatus.PAID) {
            throw new BusinessException("Изменить можно только бронь в статусе «Создано» или «Оплачено»"
                    + " (текущий статус: «" + status.getTitle() + "»)");
        }
        checkOpenForSale(booking.getFlight());
        return booking;
    }

    /** Проверяет новое место сразу после ввода, до вопросов о классе и цене; возвращает его в верхнем регистре. */
    public String checkSeatChange(long id, String seatNumber) throws SQLException {
        return validateSeatChange(requireEditable(id), seatNumber);
    }

    /** Физическое удаление только отменённой или просроченной брони. */
    public void delete(long id) throws SQLException {
        requireDeletable(id);
        if (!bookings.deleteById(id)) {
            throw new EntityNotFoundException("Бронирование с ID " + id + " не найдено");
        }
    }

    /** Возвращает бронь, если её можно удалить; UI вызывает до запроса подтверждения. */
    public Booking requireDeletable(long id) throws SQLException {
        Booking booking = get(id);
        if (booking.getStatus() != BookingStatus.CANCELLED && booking.getStatus() != BookingStatus.EXPIRED) {
            throw new BusinessException("Удалить можно только отменённую или просроченную бронь");
        }
        return booking;
    }

    /** Перевод брони в новый статус по матрице переходов. */
    public Booking changeStatus(long id, BookingStatus target) throws SQLException {
        // Отмена через смену статуса проходит те же проверки, что и пункт «Отменить бронь».
        if (target == BookingStatus.CANCELLED) {
            return cancel(id);
        }
        Booking booking = get(id);
        checkTransition(booking, target);
        if (target == BookingStatus.EXPIRED) {
            throw new BusinessException("Статус «" + target.getTitle() + "» устанавливается системой автоматически");
        }
        booking.setStatus(target);
        bookings.update(booking);
        return booking;
    }

    /** Отмена брони; место сразу возвращается в продажу. */
    public Booking cancel(long id) throws SQLException {
        Booking booking = requireCancellable(id);
        booking.setStatus(BookingStatus.CANCELLED);
        bookings.update(booking);
        return booking;
    }

    /** Возвращает бронь, если её можно отменить; UI вызывает до запроса подтверждения. */
    public Booking requireCancellable(long id) throws SQLException {
        Booking booking = get(id);
        if (!booking.getStatus().canChangeTo(BookingStatus.CANCELLED)) {
            throw new BusinessException("Отменить можно только бронь в статусе «Создано» или «Оплачено»"
                    + " (текущий статус: «" + booking.getStatus().getTitle() + "»)");
        }
        LocalDateTime departure = booking.getFlight().getDepartureTime();
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(departure)) {
            throw new BusinessException("Отмена невозможна: рейс " + booking.getFlight().getFlightNumber()
                    + " уже вылетел");
        }
        if (now.isAfter(departure.minus(CANCEL_LIMIT))) {
            throw new BusinessException("Отмена невозможна: до вылета менее 2 часов");
        }
        return booking;
    }

    /** Брони CREATED без оплаты дольше 30 минут переводятся в EXPIRED; возвращает их число. */
    public int expireOutdated() throws SQLException {
        return bookings.expireCreatedBefore(LocalDateTime.now().minus(EXPIRE_AFTER));
    }

    public List<Booking> searchByPassengerLastName(String text) throws SQLException {
        if (text == null || text.isBlank()) throw new BusinessException("Фамилия не может быть пустой");
        return bookings.searchByPassengerLastName(text.trim());
    }

    public List<Booking> searchByFlightNumber(String text) throws SQLException {
        if (text == null || text.isBlank()) throw new BusinessException("Номер рейса не может быть пустым");
        return bookings.searchByFlightNumber(text.trim());
    }

    public List<Booking> filterByStatus(BookingStatus status) throws SQLException {
        return list().stream().filter(b -> b.getStatus() == status).toList();
    }

    public List<Booking> filterByFareClass(FareClass fareClass) throws SQLException {
        return list().stream().filter(b -> b.getFareClass() == fareClass).toList();
    }

    public List<Booking> sortByPrice() throws SQLException {
        return list().stream().sorted(Comparator.comparing(Booking::getPrice)).toList();
    }

    public List<Booking> sortByDepartureTime() throws SQLException {
        return list().stream().sorted(Comparator.comparing(b -> b.getFlight().getDepartureTime())).toList();
    }

    /** Шесть показателей статистики системы. */
    public Map<String, Long> statistics() throws SQLException {
        List<Booking> all = list();
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("Всего пассажиров", passengers.count());
        result.put("Всего рейсов", (long) flights.findAll().size());
        result.put("Всего бронирований", (long) all.size());
        result.put("Активных броней", countByStatus(all, BookingStatus.CREATED)
                + countByStatus(all, BookingStatus.PAID) + countByStatus(all, BookingStatus.CHECKED_IN));
        result.put("Завершённых броней", countByStatus(all, BookingStatus.COMPLETED));
        result.put("Отменённых броней", countByStatus(all, BookingStatus.CANCELLED));
        return result;
    }

    private long countByStatus(List<Booking> all, BookingStatus status) {
        return all.stream().filter(b -> b.getStatus() == status).count();
    }

    /** Бизнес-правило: Переход должен быть разрешён матрицей статусов. */
    private void checkTransition(Booking booking, BookingStatus target) {
        if (!booking.getStatus().canChangeTo(target)) {
            throw new BusinessException("Переход " + booking.getStatus() + " → " + target + " недопустим");
        }
    }

    /** При изменении брони новое место не должно быть занято другой активной бронью этого рейса. */
    private String validateSeatChange(Booking booking, String seatNumber) throws SQLException {
        String seat = normalizeSeat(seatNumber);
        if (!seat.equals(booking.getSeatNumber())) {
            checkSeatFree(booking.getFlight(), seat, booking.getId());
        }
        return seat;
    }

    /** Проверяет формат номера места: ряд и буква A–F. */
    private String normalizeSeat(String seatNumber) {
        String normalized = seatNumber == null ? "" : seatNumber.trim().toUpperCase(Locale.ROOT);
        if (!SEAT.matcher(normalized).matches()) {
            throw new BusinessException("Номер места должен иметь вид 12A (ряд и буква A-F)");
        }
        return normalized;
    }

    /** Бизнес-правило: бронировать можно только рейс, открытый для продажи. */
    private void checkOpenForSale(Flight flight) {
        if (!flight.getStatus().isOpenForSale()) {
            throw new BusinessException("Рейс " + flight.getFlightNumber() + " закрыт для продажи (статус «"
                    + flight.getStatus().getTitle() + "»)");
        }
    }

    /** Бизнес-правило: место не может быть занято другой активной бронью на этом рейсе. */
    private void checkSeatFree(Flight flight, String seat, Long exceptBookingId) throws SQLException {
        boolean taken = bookings.findAll().stream()
                .filter(b -> b.getFlight().getId() == flight.getId())
                .filter(b -> b.getStatus().occupiesSeat())
                .filter(b -> exceptBookingId == null || b.getId() != exceptBookingId)
                .anyMatch(b -> b.getSeatNumber().equals(seat));
        if (taken) {
            throw new BusinessException("Место " + seat + " на рейсе " + flight.getFlightNumber() + " уже занято");
        }
    }

    /** Бизнес-правило: у пассажира не может быть двух активных броней на один рейс. */
    private void checkNoDuplicateBooking(Passenger passenger, Flight flight) throws SQLException {
        boolean duplicate = bookings.findAll().stream()
                .filter(b -> b.getFlight().getId() == flight.getId())
                .filter(b -> b.getPassenger().getId() == passenger.getId())
                .anyMatch(b -> b.getStatus().occupiesSeat());
        if (duplicate) {
            throw new BusinessException("У пассажира " + passenger.getShortName()
                    + " уже есть активная бронь на рейс " + flight.getFlightNumber());
        }
    }

    /** Бизнес-правило: цена билета должна быть положительной. */
    private void checkPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Стоимость билета должна быть больше нуля");
        }
    }

    private Passenger requirePassenger(long id) throws SQLException {
        return passengers.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Пассажир с ID " + id + " не найден"));
    }

    private Flight requireFlight(long id) throws SQLException {
        return flights.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Рейс с ID " + id + " не найден"));
    }
}
