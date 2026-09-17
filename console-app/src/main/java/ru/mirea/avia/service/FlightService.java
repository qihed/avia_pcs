package ru.mirea.avia.service;

import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.domain.FlightStatus;
import ru.mirea.avia.dto.FlightDtos.FlightAvailability;
import ru.mirea.avia.dto.FlightDtos.FlightRequest;
import ru.mirea.avia.dto.FlightDtos.FlightResponse;
import ru.mirea.avia.error.BusinessRuleException;
import ru.mirea.avia.error.EntityNotFoundException;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;
import ru.mirea.avia.jdbc.Transactions;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.FlightRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ведение расписания рейсов (FR-04, UC-02, UC-09).
 *
 * <p>Сервис следит за уникальностью пары «номер + время вылета», не даёт уменьшить салон
 * ниже проданных мест и удалить рейс с бронированиями (BR-10). Сообщения о закрытой продаже
 * (BR-01) формирует {@link BookingValidator}.</p>
 */
public class FlightService {
    private final FlightRepository flights;
    private final BookingRepository bookings;
    private final FlightValidator validator;
    private final FlightMapper mapper;
    private final Transactions transactions;
    private final Clock clock;
    private final Duration saleCloseBefore;

    public FlightService(FlightRepository flights, BookingRepository bookings,
                         FlightValidator validator, FlightMapper mapper,
                         Transactions transactions, Clock clock, Duration saleCloseBefore) {
        this.flights = flights;
        this.bookings = bookings;
        this.validator = validator;
        this.mapper = mapper;
        this.transactions = transactions;
        this.clock = clock;
        this.saleCloseBefore = saleCloseBefore;
    }

    /** Возвращает все рейсы расписания вместе с числом занятых мест (US-01). */
    public List<FlightAvailability> list() {
        LocalDateTime now = LocalDateTime.now(clock);
        return transactions.read(() -> {
            Map<Long, Integer> occupied = bookings.countActiveGroupByFlightId();
            return flights.findAll().stream()
                    .map(flight -> toAvailability(flight, occupied.getOrDefault(flight.getId(), 0), now))
                    .toList();
        });
    }

    /** Возвращает рейсы, открытые для продажи (BR-01) и имеющие свободные места (BR-02), по времени вылета. */
    public List<FlightAvailability> listOpenForSale() {
        return list().stream()
                .filter(FlightAvailability::openForSale)
                .filter(availability -> availability.freeSeats() > 0)
                .sorted(Comparator.comparing(availability -> availability.flight().departureTime()))
                .toList();
    }

    /** Возвращает рейс с загрузкой по ID или бросает E-202. */
    public FlightAvailability get(long id) {
        LocalDateTime now = LocalDateTime.now(clock);
        return transactions.read(() -> {
            Flight flight = require(id);
            return toAvailability(flight, bookings.countActiveGroupByFlightId().getOrDefault(id, 0), now);
        });
    }

    /** Возвращает число рейсов в расписании. */
    public long count() {
        return transactions.read(() -> flights.count());
    }

    /** Создаёт рейс: проверяет поля, вылет в будущем и уникальность пары «номер + время вылета». */
    public FlightResponse create(FlightRequest request) {
        FlightRequest normalized = validator.validate(request);
        checkFutureDeparture(normalized.departureTime());
        return transactions.write(() -> {
            validateNumberFree(normalized.flightNumber(), normalized.departureTime(), null);
            return mapper.toResponse(flights.insert(toEntity(normalized)));
        });
    }

    /** Изменяет рейс; вместимость не может стать меньше проданных мест (E-306). */
    public FlightResponse update(long id, FlightRequest request) {
        FlightRequest normalized = validator.validate(request);
        return transactions.write(() -> {
            Flight flight = require(id);
            validateNumberFree(normalized.flightNumber(), normalized.departureTime(), id);
            validateSeatsCoverBookings(id, toEntity(normalized));
            apply(flight, normalized);
            return mapper.toResponse(flights.update(flight));
        });
    }

    /**
     * Меняет статус рейса по матрице переходов (E-302).
     *
     * <p>Рейс с зарегистрированными пассажирами отменить нельзя (E-306): их брони
     * не переводятся в CANCELLED по матрице BR-07.</p>
     */
    public FlightResponse changeStatus(long id, FlightStatus target) {
        return transactions.write(() -> {
            Flight flight = require(id);
            if (target == FlightStatus.CANCELLED) validateNoCheckedIn(flight);
            flight.changeStatus(target);
            return mapper.toResponse(flights.update(flight));
        });
    }

    /** Проверяет правило отмены рейса (E-306) до запроса подтверждения у оператора. */
    public void checkCanChangeStatus(long id, FlightStatus target) {
        transactions.read(() -> {
            Flight flight = require(id);
            if (target == FlightStatus.CANCELLED) validateNoCheckedIn(flight);
            return null;
        });
    }

    /** Проверяет правило BR-10 до запроса подтверждения у оператора (E-305). */
    public void checkCanDelete(long id) {
        transactions.read(() -> {
            validateNoBookings(require(id));
            return null;
        });
    }

    /** Удаляет рейс, на который не оформлено ни одной брони (BR-10). */
    public void delete(long id) {
        transactions.write(() -> {
            validateNoBookings(require(id));
            return flights.deleteById(id);
        });
    }

    /** Проверяет, что в расписании нет другого рейса с тем же номером и временем вылета (E-307). */
    public void checkNumberFree(String flightNumber, LocalDateTime departureTime, Long exceptId) {
        transactions.read(() -> {
            validateNumberFree(flightNumber, departureTime, exceptId);
            return null;
        });
    }

    /** Проверяет, что вылет нового рейса в будущем (E-102), и возвращает время без изменений. */
    public LocalDateTime checkFutureDeparture(LocalDateTime departureTime) {
        if (!departureTime.isAfter(LocalDateTime.now(clock))) {
            throw new ValidationException(ErrorCode.E_102, "Время вылета нового рейса должно быть в будущем");
        }
        return departureTime;
    }

    /** Возвращает валидатор полей для пошаговых диалогов ввода. */
    public FlightValidator validator() {
        return validator;
    }

    private Flight require(long id) {
        return flights.findById(id).orElseThrow(() -> EntityNotFoundException.flight(id));
    }

    private FlightAvailability toAvailability(Flight flight, int occupied, LocalDateTime now) {
        int free = Math.max(0, flight.getTotalSeats() - occupied);
        return new FlightAvailability(mapper.toResponse(flight), occupied, free,
                flight.isOpenForSale(now, saleCloseBefore));
    }

    private void validateNumberFree(String flightNumber, LocalDateTime departureTime, Long exceptId) {
        flights.findByFlightNumberAndDepartureTime(flightNumber, departureTime)
                .filter(other -> !other.getId().equals(exceptId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(ErrorCode.E_307, "Рейс " + flightNumber
                            + " с таким временем вылета уже есть в расписании (ID " + other.getId() + ")");
                });
    }

    /**
     * Проверяет, что салон {@code resized} вмещает все активные брони рейса (BR-02).
     *
     * <p>Уже проданные места должны остаться в пределах нового числа рядов.</p>
     */
    private void validateSeatsCoverBookings(long flightId, Flight resized) {
        List<Booking> active = bookings.findActiveByFlightId(flightId);
        if (resized.getTotalSeats() < active.size()) {
            throw new BusinessRuleException(ErrorCode.E_306, "Нельзя уменьшить число мест до "
                    + resized.getTotalSeats() + ": на рейс оформлено активных броней: " + active.size());
        }
        active.stream()
                .filter(booking -> seatRow(booking.getSeatNumber()) > resized.getRowCount())
                .findFirst()
                .ifPresent(booking -> {
                    throw new BusinessRuleException(ErrorCode.E_306, "Нельзя уменьшить число мест до "
                            + resized.getTotalSeats() + ": место " + booking.getSeatNumber() + " (бронь "
                            + booking.getBookingRef() + ") окажется за пределами салона");
                });
    }

    private void validateNoCheckedIn(Flight flight) {
        long checkedIn = bookings.findActiveByFlightId(flight.getId()).stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CHECKED_IN)
                .count();
        if (checkedIn > 0) {
            throw new BusinessRuleException(ErrorCode.E_306, "Нельзя отменить рейс " + flight.getFlightNumber()
                    + ": на него уже зарегистрированы пассажиры (" + checkedIn + ")");
        }
    }

    private void validateNoBookings(Flight flight) {
        long count = bookings.countByFlightId(flight.getId());
        if (count > 0) {
            throw new BusinessRuleException(ErrorCode.E_305, "Нельзя удалить рейс " + flight.getFlightNumber()
                    + ": на него оформлены бронирования (" + count + ")");
        }
    }

    private static Flight toEntity(FlightRequest request) {
        return new Flight(request.flightNumber(), request.airline(), request.departureAirport(),
                request.arrivalAirport(), request.departureTime(), request.arrivalTime(),
                request.aircraftType(), request.totalSeats(), request.basePrice());
    }

    private static void apply(Flight flight, FlightRequest request) {
        flight.setFlightNumber(request.flightNumber());
        flight.setAirline(request.airline());
        flight.setRoute(request.departureAirport(), request.arrivalAirport());
        flight.setSchedule(request.departureTime(), request.arrivalTime());
        flight.setAircraftType(request.aircraftType());
        flight.setTotalSeats(request.totalSeats());
        flight.setBasePrice(request.basePrice());
    }

    private static int seatRow(String seatNumber) {
        return Integer.parseInt(seatNumber.substring(0, seatNumber.length() - 1));
    }
}
