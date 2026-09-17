package ru.mirea.avia.service;

import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.domain.Passenger;
import ru.mirea.avia.dto.BookingDtos.BookingChangeRequest;
import ru.mirea.avia.dto.BookingDtos.BookingRequest;
import ru.mirea.avia.dto.BookingDtos.BookingResponse;
import ru.mirea.avia.dto.BookingDtos.PriceQuote;
import ru.mirea.avia.error.DataAccessException;
import ru.mirea.avia.error.EntityNotFoundException;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.jdbc.Transactions;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.FlightRepository;
import ru.mirea.avia.repository.PassengerRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Прикладной сервис основной сущности «Бронирование» (FR-05…FR-11, FR-19…FR-21).
 *
 * <p>Правила BR-01…BR-10 проверяет {@link BookingValidator}, стоимость считает
 * {@link FareCalculator}, а сервис открывает транзакции и возвращает DTO. Перед операциями,
 * которые зависят от занятости мест и статусов, неоплаченные брони аннулируются (BR-09).</p>
 */
public class BookingService {
    private static final int PNR_ATTEMPTS = 5;
    private static final Pattern NUMERIC_ID = Pattern.compile("^\\d{1,18}$");

    private final BookingRepository bookings;
    private final PassengerRepository passengers;
    private final FlightRepository flights;
    private final BookingValidator validator;
    private final FareCalculator fareCalculator;
    private final PnrGenerator pnrGenerator;
    private final BookingMapper mapper;
    private final Transactions transactions;
    private final Clock clock;
    private final Duration expireAfter;

    public BookingService(BookingRepository bookings, PassengerRepository passengers,
                          FlightRepository flights, BookingValidator validator,
                          FareCalculator fareCalculator, PnrGenerator pnrGenerator,
                          BookingMapper mapper, Transactions transactions,
                          Clock clock, Duration expireAfter) {
        this.bookings = bookings;
        this.passengers = passengers;
        this.flights = flights;
        this.validator = validator;
        this.fareCalculator = fareCalculator;
        this.pnrGenerator = pnrGenerator;
        this.mapper = mapper;
        this.transactions = transactions;
        this.clock = clock;
        this.expireAfter = expireAfter;
    }

    /** Создаёт бронь в статусе CREATED с рассчитанной стоимостью и уникальным PNR (UC-03). */
    public BookingResponse create(BookingRequest request) {
        // Просроченные брони должны освободить места до проверок BR-02 и BR-03.
        expireOutdated();
        return transactions.write(() -> {
            Passenger passenger = requirePassenger(request.passengerId());
            Flight flight = requireFlight(request.flightId());
            String seat = validator.normalizeSeat(request.seatNumber());
            validateFlight(passenger, flight);
            validateSeat(flight, seat, null);
            boolean baggage = normalizeBaggage(request.fareClass(), request.baggageIncluded());
            PriceQuote quote = fareCalculator.calculate(flight.getBasePrice(), request.fareClass(), baggage);
            Booking booking = new Booking(generateUniqueRef(), passenger, flight, seat, request.fareClass(),
                    baggage, quote.total());
            booking.onCreate(now());
            return mapper.toResponse(bookings.insert(booking));
        });
    }

    /** Проверяет выбранный в диалоге рейс для пассажира: BR-05, BR-01, BR-02, BR-04. */
    public void checkFlightAvailable(long passengerId, long flightId) {
        transactions.read(() -> {
            validateFlight(requirePassenger(passengerId), requireFlight(flightId));
            return null;
        });
    }

    /** Проверяет формат, ряд и занятость места (BR-03) и возвращает нормализованный номер. */
    public String checkSeatAvailable(long flightId, String seatNumber) {
        return transactions.read(() -> {
            String seat = validator.normalizeSeat(seatNumber);
            validateSeat(requireFlight(flightId), seat, null);
            return seat;
        });
    }

    /** Возвращает расчёт стоимости билета до сохранения брони (US-02, BR-06). */
    public PriceQuote quote(long flightId, FareClass fareClass, boolean baggageIncluded) {
        return transactions.read(() ->
                fareCalculator.calculate(requireFlight(flightId).getBasePrice(), fareClass, baggageIncluded));
    }

    /** Возвращает все брони после аннулирования просроченных (BR-09). */
    public List<BookingResponse> list() {
        expireOutdated();
        return transactions.read(() -> bookings.findAll().stream()
                .map(mapper::toResponse)
                .toList());
    }

    /** Возвращает бронь по ID. */
    public BookingResponse get(long id) {
        return transactions.read(() -> mapper.toResponse(require(id)));
    }

    /** Возвращает бронь по введённому значению: число — ID, иначе — номер PNR. */
    public BookingResponse find(String idOrRef) {
        expireOutdated();
        String value = idOrRef == null ? "" : idOrRef.trim();
        return transactions.read(() -> {
            Booking booking = NUMERIC_ID.matcher(value).matches()
                    ? require(Long.parseLong(value))
                    : requireByRef(value);
            return mapper.toResponse(booking);
        });
    }

    /** Возвращает общее число броней. */
    public long count() {
        return transactions.read(bookings::count);
    }

    /** Возвращает число активных броней после аннулирования просроченных (BR-09). */
    public long countActive() {
        expireOutdated();
        return transactions.read(bookings::countActive);
    }

    /** Проверяет, что бронь можно изменить: статус CREATED или PAID и рейс в продаже. */
    public void checkCanModify(long id) {
        transactions.read(() -> {
            validator.checkCanModify(require(id));
            return null;
        });
    }

    /** Изменяет место, класс и багаж брони с пересчётом стоимости (FR-08, BR-03, BR-06). */
    public BookingResponse update(long id, BookingChangeRequest request) {
        expireOutdated();
        return transactions.write(() -> {
            Booking booking = require(id);
            validator.checkCanModify(booking);
            String seat = validator.normalizeSeat(request.seatNumber());
            // Неизменённое место уже закреплено за этой бронью, поэтому повторная проверка не нужна.
            if (!seat.equals(booking.getSeatNumber())) {
                validateSeat(booking.getFlight(), seat, booking.getId());
            }
            boolean baggage = normalizeBaggage(request.fareClass(), request.baggageIncluded());
            PriceQuote quote = fareCalculator.calculate(booking.getFlight().getBasePrice(), request.fareClass(),
                    baggage);
            booking.modify(seat, request.fareClass(), baggage, quote.total(), now());
            return mapper.toResponse(bookings.update(booking));
        });
    }

    /** Изменяет статус брони по матрице BR-07 с временными условиями и правилом BR-08. */
    public BookingResponse changeStatus(long id, BookingStatus target) {
        expireOutdated();
        return transactions.write(() -> {
            Booking booking = require(id);
            validator.checkStatusChange(booking, target);
            booking.changeStatus(target, now());
            return mapper.toResponse(bookings.update(booking));
        });
    }

    /** Проверяет возможность отмены до запроса подтверждения у оператора (BR-08). */
    public void checkCanCancel(long id) {
        transactions.read(() -> {
            validator.checkCanCancel(require(id));
            return null;
        });
    }

    /** Отменяет бронь; место сразу возвращается в продажу (FR-10, BR-08). */
    public BookingResponse cancel(long id) {
        return changeStatus(id, BookingStatus.CANCELLED);
    }

    /** Аннулирует брони CREATED, не оплаченные в установленный срок, и возвращает их число (BR-09). */
    public int expireOutdated() {
        return transactions.write(() -> {
            LocalDateTime now = now();
            return bookings.expireCreatedBefore(now.minus(expireAfter), now);
        });
    }

    /** Проверяет возможность удаления до запроса подтверждения у оператора (BR-10). */
    public void checkCanDelete(long id) {
        transactions.read(() -> {
            validator.checkCanDelete(require(id));
            return null;
        });
    }

    /** Удаляет бронь физически — только в статусах CANCELLED и EXPIRED (FR-11, BR-10). */
    public void delete(long id) {
        transactions.write(() -> {
            validator.checkCanDelete(require(id));
            return bookings.deleteById(id);
        });
    }

    /** Возвращает срок оплаты, после которого бронь CREATED аннулируется (BR-09). */
    public Duration expireAfter() {
        return expireAfter;
    }

    /** Возвращает номер места в каноническом виде: {@code 14c} → {@code 14C}. */
    public String normalizeSeat(String seat) {
        return validator.normalizeSeat(seat);
    }

    private Booking require(long id) {
        return bookings.findById(id).orElseThrow(() -> EntityNotFoundException.booking(id));
    }

    private Booking requireByRef(String value) {
        String ref = validator.normalizeRef(value);
        return bookings.findByBookingRef(ref).orElseThrow(() -> EntityNotFoundException.bookingRef(ref));
    }

    private Passenger requirePassenger(long id) {
        return passengers.findById(id).orElseThrow(() -> EntityNotFoundException.passenger(id));
    }

    private Flight requireFlight(long id) {
        return flights.findById(id).orElseThrow(() -> EntityNotFoundException.flight(id));
    }

    private void validateFlight(Passenger passenger, Flight flight) {
        validator.checkOpenForSale(flight);
        validator.checkCapacity(flight, bookings.findActiveByFlightId(flight.getId()));
        validator.checkNoDuplicate(passenger, flight);
    }

    private void validateSeat(Flight flight, String seat, Long exceptBookingId) {
        validator.checkSeatInCabin(flight, seat);
        validator.checkSeatFree(flight, seat, bookings.findActiveByFlightId(flight.getId()), exceptBookingId);
    }

    private String generateUniqueRef() {
        for (int attempt = 0; attempt < PNR_ATTEMPTS; attempt++) {
            String ref = pnrGenerator.generate();
            if (!bookings.existsByBookingRef(ref)) return ref;
        }
        throw new DataAccessException(ErrorCode.E_502, "Failed to generate unique booking reference", null);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static boolean normalizeBaggage(FareClass fareClass, boolean requested) {
        // В бизнес-классе багаж входит в тариф, поэтому признак всегда включён.
        return requested || fareClass.isBaggageIncluded();
    }
}
