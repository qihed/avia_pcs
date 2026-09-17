package ru.mirea.avia.service;

import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.dto.BookingDtos.BookingResponse;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;
import ru.mirea.avia.filter.BookingFilter;
import ru.mirea.avia.filter.BookingSort;
import ru.mirea.avia.filter.BookingSpecifications;
import ru.mirea.avia.filter.SmartFilterParser;
import ru.mirea.avia.jdbc.Transactions;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.FlightRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Поиск (FR-12, SR-01…SR-06), фильтрация (FR-13, FL-01…FL-05) и сортировка (FR-14, SO-01…SO-05) броней.
 *
 * <p>SR-01…SR-04 выполняются параметризованным SQL на стороне СУБД, SR-05 и SR-06 — средствами
 * Stream API над выборкой рейсов. Фильтр и сортировка применяются к полной выборке броней
 * через {@link BookingSpecifications} и {@link BookingSort#comparator()}.</p>
 */
public class BookingSearchService {
    private static final Pattern FLIGHT_ID = Pattern.compile("^\\d{1,18}$");

    private final BookingRepository bookings;
    private final FlightRepository flights;
    private final BookingValidator bookingValidator;
    private final FlightValidator flightValidator;
    private final SmartFilterParser parser;
    private final BookingMapper mapper;
    private final Transactions transactions;

    public BookingSearchService(BookingRepository bookings, FlightRepository flights,
                                BookingValidator bookingValidator, FlightValidator flightValidator,
                                SmartFilterParser parser, BookingMapper mapper,
                                Transactions transactions) {
        this.bookings = bookings;
        this.flights = flights;
        this.bookingValidator = bookingValidator;
        this.flightValidator = flightValidator;
        this.parser = parser;
        this.mapper = mapper;
        this.transactions = transactions;
    }

    /** Возвращает бронь по номеру PNR без учёта регистра: пустой список или одну запись (SR-01). */
    public List<BookingResponse> byRef(String bookingRef) {
        String ref = bookingValidator.normalizeRef(bookingRef);
        return transactions.read(() -> bookings.findByBookingRef(ref).stream()
                .map(mapper::toResponse)
                .toList());
    }

    /** Возвращает брони пассажиров, фамилия которых содержит заданную часть без учёта регистра (SR-02). */
    public List<BookingResponse> byLastName(String lastNamePart) {
        String part = lastNamePart == null ? "" : lastNamePart.trim();
        if (part.isEmpty()) {
            throw new ValidationException(ErrorCode.E_104, "Фамилия пассажира не может быть пустой");
        }
        return transactions.read(() -> toResponses(bookings.findByPassengerLastNameContainingIgnoreCase(part)));
    }

    /** Возвращает брони по точному номеру документа пассажира; пробелы в номере игнорируются (SR-03). */
    public List<BookingResponse> byDocumentNumber(String documentNumber) {
        String compact = documentNumber == null ? "" : documentNumber.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (compact.isEmpty()) {
            throw new ValidationException(ErrorCode.E_104, "Номер документа не может быть пустым");
        }
        String withoutDashes = DocumentType.PASSPORT_RF.normalize(compact);
        return transactions.read(() -> {
            List<Booking> found = bookings.findByPassengerDocumentNumber(compact);
            // Свидетельство о рождении хранится с дефисами, остальные документы — без них.
            if (found.isEmpty() && !withoutDashes.equals(compact)) {
                found = bookings.findByPassengerDocumentNumber(withoutDashes);
            }
            return toResponses(found);
        });
    }

    /** Возвращает брони на рейсы с указанным номером, точное совпадение (SR-04). */
    public List<BookingResponse> byFlightNumber(String flightNumber) {
        String number = flightValidator.normalizeFlightNumber(flightNumber);
        return transactions.read(() -> toResponses(bookings.findByFlightNumber(number)));
    }

    /** Возвращает брони на рейсы по маршруту «откуда — куда» (SR-05). */
    public List<BookingResponse> byRoute(String from, String to) {
        String departure = flightValidator.normalizeAirport(from);
        String arrival = flightValidator.normalizeAirport(to);
        Predicate<Flight> route = flight -> flight.getDepartureAirport().equals(departure)
                && flight.getArrivalAirport().equals(arrival);
        return transactions.read(() -> bookingsOfFlights(route));
    }

    /** Возвращает брони на рейсы, вылетающие в указанную дату (SR-06). */
    public List<BookingResponse> byDepartureDate(LocalDate date) {
        Predicate<Flight> sameDate = flight -> flight.getDepartureTime().toLocalDate().equals(date);
        return transactions.read(() -> bookingsOfFlights(sameDate));
    }

    /** Возвращает брони, отобранные фильтром и упорядоченные выбранной сортировкой (FR-13, FR-14). */
    public List<BookingResponse> search(BookingFilter filter, BookingSort sort) {
        BookingFilter criteria = filter == null ? BookingFilter.empty() : filter;
        BookingSort order = sort == null ? BookingSort.BY_ID : sort;
        Predicate<Booking> condition = BookingSpecifications.from(criteria);
        return transactions.read(() -> bookings.findAll().stream()
                .filter(condition)
                .sorted(order.comparator())
                .map(mapper::toResponse)
                .toList());
    }

    /** Разбирает строку умного фильтра и проверяет в ней рейс и коды аэропортов. */
    public BookingFilter parse(String query) {
        return normalizeFilter(parser.parse(query));
    }

    /** Проверяет критерий FL-03: числовой ID рейса возвращается как есть, номер рейса — нормализованным. */
    public String flightCriterion(String value) {
        String criterion = value == null ? "" : value.trim();
        if (FLIGHT_ID.matcher(criterion).matches()) return criterion;
        return flightValidator.normalizeFlightNumber(criterion);
    }

    /** Проверяет код аэропорта IATA и приводит его к верхнему регистру. */
    public String normalizeAirport(String code) {
        return flightValidator.normalizeAirport(code);
    }

    /** Проверяет номер рейса и приводит его к виду SU1416. */
    public String normalizeFlightNumber(String number) {
        return flightValidator.normalizeFlightNumber(number);
    }

    private List<BookingResponse> bookingsOfFlights(Predicate<Flight> flightCondition) {
        Set<Long> flightIds = flights.findAll().stream()
                .filter(flightCondition)
                .map(Flight::getId)
                .collect(Collectors.toSet());
        if (flightIds.isEmpty()) return List.of();
        return bookings.findAll().stream()
                .filter(booking -> flightIds.contains(booking.getFlight().getId()))
                .sorted(BookingSort.BY_DEPARTURE.comparator())
                .map(mapper::toResponse)
                .toList();
    }

    private List<BookingResponse> toResponses(List<Booking> source) {
        return source.stream()
                .map(mapper::toResponse)
                .toList();
    }

    private BookingFilter normalizeFilter(BookingFilter filter) {
        String flight = filter.flight() == null ? null : flightCriterion(filter.flight());
        String from = filter.from() == null ? null : normalizeAirport(filter.from());
        String to = filter.to() == null ? null : normalizeAirport(filter.to());
        return new BookingFilter(filter.text(), filter.status(), filter.inProgressOnly(), filter.fareClass(),
                flight, from, to, filter.passenger(), filter.departureFrom(), filter.departureTo(),
                filter.prices());
    }
}
