package ru.mirea.avia.service;

import ru.mirea.avia.config.AppProperties.BookingRules;
import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.domain.FlightStatus;
import ru.mirea.avia.domain.Passenger;
import ru.mirea.avia.error.BusinessRuleException;
import ru.mirea.avia.error.DuplicateBookingException;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.InvalidStatusTransitionException;
import ru.mirea.avia.error.SeatUnavailableException;
import ru.mirea.avia.error.ValidationException;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.util.DateTimeFormat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Проверяет бизнес-правила бронирования до записи в БД.
 *
 * <p>Здесь собраны BR-01…BR-04 (создание и изменение брони), BR-07 и BR-08 (смена статуса
 * и отмена), BR-10 (удаление), а также форматы номера места и PNR. Поэтому
 * {@link BookingService} только управляет транзакциями и не дублирует правила.</p>
 */
public final class BookingValidator {
    /** Регистрация на рейс открывается за 24 часа до вылета (рис. 5.3 ТЗ). */
    public static final Duration CHECK_IN_OPENS_BEFORE = Duration.ofHours(24);
    private static final Pattern SEAT = Pattern.compile("^([0-9]{1,2})([A-F])$");
    private static final Pattern PNR = Pattern.compile("^[A-Z0-9]{6}$");
    private static final String FINAL_NOTE = "Бронь находится в финальном состоянии и не может быть изменена";

    private final BookingRepository bookings;
    private final Clock clock;
    private final BookingRules rules;

    public BookingValidator(BookingRepository bookings, Clock clock, BookingRules rules) {
        this.bookings = bookings;
        this.clock = clock;
        this.rules = rules;
    }

    /** Возвращает номер места вида «ряд + буква A–F»: {@code 14c} → {@code 14C} (E-103). */
    public String normalizeSeat(String seat) {
        String normalized = seat == null ? "" : seat.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = SEAT.matcher(normalized);
        if (!matcher.matches() || Integer.parseInt(matcher.group(1)) < 1) {
            throw new ValidationException(ErrorCode.E_103, "Номер места должен иметь вид 12A (ряд и буква A–F)");
        }
        return Integer.parseInt(matcher.group(1)) + matcher.group(2);
    }

    /** Возвращает номер брони PNR в верхнем регистре: шесть латинских букв и цифр (E-103). */
    public String normalizeRef(String ref) {
        String normalized = ref == null ? "" : ref.trim().toUpperCase(Locale.ROOT);
        if (!PNR.matcher(normalized).matches()) {
            throw new ValidationException(ErrorCode.E_103,
                    "Номер брони должен состоять из 6 латинских букв и цифр, например AB12CD");
        }
        return normalized;
    }

    /**
     * Проверяет, что рейс открыт для продажи (BR-01, E-300).
     *
     * <p>Бронировать можно только рейс в статусе SCHEDULED или DELAYED, до вылета которого
     * остаётся не меньше срока закрытия продажи.</p>
     */
    public void checkOpenForSale(Flight flight) {
        LocalDateTime now = now();
        if (flight.isOpenForSale(now, rules.saleCloseBefore())) return;
        String number = flight.getFlightNumber();
        String message = switch (flight.getStatus()) {
            case CANCELLED -> "Рейс " + number + " отменён и закрыт для продажи";
            case DEPARTED -> "Рейс " + number + " уже вылетел и закрыт для продажи";
            case ARRIVED -> "Рейс " + number + " уже выполнен и закрыт для продажи";
            case SCHEDULED, DELAYED -> flight.hasDeparted(now)
                    ? "Рейс " + number + " уже вылетел, продажа закрыта"
                    : "Продажа на рейс " + number + " закрыта: до вылета осталось менее "
                            + rules.saleCloseBefore().toMinutes() + " минут";
        };
        throw new BusinessRuleException(ErrorCode.E_300, message);
    }

    /** Проверяет, что ряд места существует в салоне рейса: 6 мест в ряду (CON-09, E-103). */
    public void checkSeatInCabin(Flight flight, String seat) {
        if (seatRow(seat) > flight.getRowCount()) {
            throw new ValidationException(ErrorCode.E_103, "На рейсе " + flight.getFlightNumber()
                    + " ряды с 1 по " + flight.getRowCount());
        }
    }

    /** Проверяет, что активных броней меньше вместимости рейса (BR-02, E-301). */
    public void checkCapacity(Flight flight, List<Booking> active) {
        if (active.size() >= flight.getTotalSeats()) {
            throw new SeatUnavailableException("На рейсе " + flight.getFlightNumber() + " нет свободных мест");
        }
    }

    /** Проверяет, что место не занято другой активной бронью рейса (BR-03, E-301). */
    public void checkSeatFree(Flight flight, String seat, List<Booking> active, Long exceptBookingId) {
        // При смене места собственная бронь не считается конфликтующей.
        boolean taken = active.stream()
                .filter(booking -> !Objects.equals(booking.getId(), exceptBookingId))
                .anyMatch(booking -> booking.getSeatNumber().equals(seat));
        if (taken) {
            throw new SeatUnavailableException("Место " + seat + " на рейсе " + flight.getFlightNumber()
                    + " уже занято");
        }
    }

    /** Проверяет, что у пассажира нет другой активной брони на этот рейс (BR-04, E-303). */
    public void checkNoDuplicate(Passenger passenger, Flight flight) {
        bookings.findActiveByFlightIdAndPassengerId(flight.getId(), passenger.getId())
                .ifPresent(existing -> {
                    throw new DuplicateBookingException("У пассажира уже есть бронь " + existing.getBookingRef()
                            + " на рейс " + flight.getFlightNumber());
                });
    }

    /** Проверяет, что бронь можно изменить: статус CREATED или PAID (E-306) и рейс в продаже (BR-01). */
    public void checkCanModify(Booking booking) {
        if (!booking.canBeModified()) {
            throw new BusinessRuleException(ErrorCode.E_306, "Изменить можно только бронь в статусе «"
                    + BookingStatus.CREATED.getShortTitle() + "» или «" + BookingStatus.PAID.getShortTitle()
                    + "». Текущий статус брони " + booking.getBookingRef()
                    + ": «" + booking.getStatus().getTitle() + "»");
        }
        checkOpenForSale(booking.getFlight());
    }

    /**
     * Проверяет переход статуса по матрице BR-07 и временным условиям рис. 5.3 ТЗ.
     *
     * <p>Оплата возможна до вылета, регистрация — за 24 часа до вылета, завершение — после
     * прилёта, отмена — по правилу BR-08. Статус EXPIRED вручную не устанавливается.</p>
     */
    public void checkStatusChange(Booking booking, BookingStatus target) {
        BookingStatus current = booking.getStatus();
        if (target == BookingStatus.EXPIRED && current.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException("Статус EXPIRED устанавливается системой автоматически "
                    + "для броней, не оплаченных вовремя (BR-09)");
        }
        if (!current.canTransitionTo(target)) {
            throw InvalidStatusTransitionException.of(current, target, current.isFinal() ? FINAL_NOTE : null);
        }
        switch (target) {
            case PAID -> validateBeforeDeparture(booking.getFlight(), "Оплата");
            case CHECKED_IN -> validateCheckInWindow(booking.getFlight());
            case COMPLETED -> validateArrived(booking.getFlight());
            case CANCELLED -> checkCanCancel(booking);
            case CREATED, EXPIRED -> {
                // Переход в CREATED матрица BR-07 не допускает, а EXPIRED отклонён выше.
            }
        }
    }

    /**
     * Проверяет, что бронь можно отменить (BR-08).
     *
     * <p>Отмена возможна из статусов CREATED и PAID (иначе E-302) не позднее установленного
     * срока до вылета (иначе E-304). Бронь на рейс, отменённый авиакомпанией, можно отменить
     * в любой момент.</p>
     */
    public void checkCanCancel(Booking booking) {
        if (!booking.canBeCancelled()) {
            BookingStatus current = booking.getStatus();
            throw InvalidStatusTransitionException.of(current, BookingStatus.CANCELLED, current.isFinal()
                    ? FINAL_NOTE
                    : "Отменить можно только бронь в статусе «Создано» или «Оплачено»");
        }
        Flight flight = booking.getFlight();
        // Пассажир рейса, отменённого авиакомпанией, не должен терять возможность отменить бронь.
        if (flight.getStatus() == FlightStatus.CANCELLED) return;
        LocalDateTime now = now();
        if (flight.hasDeparted(now)) {
            throw new BusinessRuleException(ErrorCode.E_304,
                    "Отмена невозможна: рейс " + flight.getFlightNumber() + " уже вылетел");
        }
        Duration limit = rules.cancelNotLaterThan();
        if (now.plus(limit).isAfter(flight.getDepartureTime())) {
            throw new BusinessRuleException(ErrorCode.E_304,
                    "Отмена невозможна: до вылета осталось менее " + hoursText(limit.toHours()));
        }
    }

    /** Проверяет, что удаляется только отменённая или просроченная бронь (BR-10, E-305). */
    public void checkCanDelete(Booking booking) {
        if (!booking.canBeDeleted()) {
            throw new BusinessRuleException(ErrorCode.E_305,
                    "Удалить можно только отменённую или просроченную бронь. Текущий статус брони "
                            + booking.getBookingRef() + ": «" + booking.getStatus().getTitle() + "»");
        }
    }

    /** Возвращает число часов в форме после слова «менее»: «1 часа», «2 часов», «21 часа». */
    static String hoursText(long hours) {
        boolean singular = hours % 10 == 1 && hours % 100 != 11;
        return hours + (singular ? " часа" : " часов");
    }

    private void validateBeforeDeparture(Flight flight, String operation) {
        if (flight.getStatus() == FlightStatus.CANCELLED) {
            throw new BusinessRuleException(ErrorCode.E_306,
                    operation + " невозможна: рейс " + flight.getFlightNumber() + " отменён");
        }
        if (flight.hasDeparted(now())) {
            throw new BusinessRuleException(ErrorCode.E_306,
                    operation + " невозможна: рейс " + flight.getFlightNumber() + " уже вылетел");
        }
    }

    private void validateCheckInWindow(Flight flight) {
        validateBeforeDeparture(flight, "Регистрация");
        LocalDateTime opensAt = flight.getDepartureTime().minus(CHECK_IN_OPENS_BEFORE);
        if (now().isBefore(opensAt)) {
            throw new BusinessRuleException(ErrorCode.E_306, "Регистрация на рейс " + flight.getFlightNumber()
                    + " открывается за 24 часа до вылета — с " + DateTimeFormat.format(opensAt));
        }
    }

    private void validateArrived(Flight flight) {
        if (flight.getStatus() == FlightStatus.CANCELLED) {
            throw new BusinessRuleException(ErrorCode.E_306,
                    "Перелёт не может быть выполнен: рейс " + flight.getFlightNumber() + " отменён");
        }
        if (flight.getStatus() != FlightStatus.ARRIVED && now().isBefore(flight.getArrivalTime())) {
            throw new BusinessRuleException(ErrorCode.E_306, "Перелёт ещё не выполнен: прилёт рейса "
                    + flight.getFlightNumber() + " ожидается " + DateTimeFormat.format(flight.getArrivalTime()));
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static int seatRow(String seat) {
        return Integer.parseInt(seat.substring(0, seat.length() - 1));
    }
}
