package ru.mirea.avia.repository.impl;

import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.error.EntityNotFoundException;
import ru.mirea.avia.jdbc.DatabaseManager;
import ru.mirea.avia.repository.BookingRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Persistence gateway for bookings.
 *
 * <p>A booking is read by one JOIN query and assembled together with its passenger ({@code p_*}
 * columns) and flight ({@code f_*} columns). Active bookings are filtered by
 * {@code status NOT IN (CANCELLED, EXPIRED)}, the same condition as in the partial unique indexes.</p>
 */
public final class BookingRepositoryImpl extends AbstractJdbcRepository implements BookingRepository {
    private static final String SELECT = """
            SELECT b.id, b.booking_ref, b.seat_number, b.fare_class, b.baggage_included,
                   b.price, b.status, b.created_at, b.updated_at,
                   p.id AS p_id, p.last_name AS p_last_name, p.first_name AS p_first_name,
                   p.middle_name AS p_middle_name, p.birth_date AS p_birth_date,
                   p.document_type AS p_document_type, p.document_number AS p_document_number,
                   p.email AS p_email, p.phone AS p_phone, p.created_at AS p_created_at,
                   f.id AS f_id, f.flight_number AS f_flight_number, f.airline AS f_airline,
                   f.departure_airport AS f_departure_airport, f.arrival_airport AS f_arrival_airport,
                   f.departure_time AS f_departure_time, f.arrival_time AS f_arrival_time,
                   f.aircraft_type AS f_aircraft_type, f.total_seats AS f_total_seats,
                   f.base_price AS f_base_price, f.status AS f_status
            FROM bookings b
            JOIN passengers p ON p.id = b.passenger_id
            JOIN flights f ON f.id = b.flight_id
            """;
    private static final String SEARCH_ORDER = " ORDER BY f.departure_time, b.id";
    private static final String FIND_BY_ID = SELECT + "WHERE b.id = ?";
    private static final String FIND_ALL = SELECT + "ORDER BY b.id";
    private static final String FIND_BY_REF = SELECT + "WHERE b.booking_ref = ?";
    private static final String FIND_BY_LAST_NAME = SELECT + "WHERE LOWER(p.last_name) LIKE LOWER(?)" + SEARCH_ORDER;
    private static final String FIND_BY_DOCUMENT_NUMBER = SELECT + "WHERE p.document_number = ?" + SEARCH_ORDER;
    private static final String FIND_BY_FLIGHT_NUMBER = SELECT + "WHERE f.flight_number = ?" + SEARCH_ORDER;
    private static final String FIND_ACTIVE_BY_FLIGHT = SELECT
            + "WHERE b.flight_id = ? AND b.status NOT IN (?, ?) ORDER BY b.id";
    private static final String FIND_ACTIVE_BY_FLIGHT_AND_PASSENGER = SELECT
            + "WHERE b.flight_id = ? AND b.passenger_id = ? AND b.status NOT IN (?, ?)";
    private static final String INSERT = """
            INSERT INTO bookings (booking_ref, passenger_id, flight_id, seat_number, fare_class,
                                  baggage_included, price, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE = """
            UPDATE bookings
            SET seat_number = ?, fare_class = ?, baggage_included = ?, price = ?,
                status = ?, updated_at = ?
            WHERE id = ?
            """;
    private static final String EXPIRE = """
            UPDATE bookings
            SET status = ?, updated_at = ?
            WHERE status = ? AND created_at < ?
            """;
    private static final String DELETE = "DELETE FROM bookings WHERE id = ?";
    private static final String COUNT = "SELECT count(*) FROM bookings";
    private static final String COUNT_ACTIVE = "SELECT count(*) FROM bookings WHERE status NOT IN (?, ?)";
    private static final String COUNT_BY_REF = "SELECT count(*) FROM bookings WHERE booking_ref = ?";
    private static final String COUNT_BY_PASSENGER = "SELECT count(*) FROM bookings WHERE passenger_id = ?";
    private static final String COUNT_BY_FLIGHT = "SELECT count(*) FROM bookings WHERE flight_id = ?";
    private static final String COUNT_ACTIVE_GROUP_BY_FLIGHT = """
            SELECT flight_id, count(*)
            FROM bookings
            WHERE status NOT IN (?, ?)
            GROUP BY flight_id
            """;
    private static final String PASSENGER_PREFIX = "p_";
    private static final String FLIGHT_PREFIX = "f_";

    public BookingRepositoryImpl(DatabaseManager database) {
        super(database);
    }

    @Override
    public Booking insert(Booking booking) {
        long id = insertAndGetId(INSERT, "Failed to insert booking",
                booking.getBookingRef(), booking.getPassenger().getId(), booking.getFlight().getId(),
                booking.getSeatNumber(), booking.getFareClass(), booking.isBaggageIncluded(), booking.getPrice(),
                booking.getStatus(), booking.getCreatedAt(), booking.getUpdatedAt());
        booking.setId(id);
        return booking;
    }

    @Override
    public Optional<Booking> findById(Long id) {
        return queryForObject(FIND_BY_ID, BookingRepositoryImpl::map, "Failed to find booking by id", id);
    }

    @Override
    public List<Booking> findAll() {
        return queryForList(FIND_ALL, BookingRepositoryImpl::map, "Failed to list bookings");
    }

    @Override
    public Booking update(Booking booking) {
        int rows = executeUpdate(UPDATE, "Failed to update booking",
                booking.getSeatNumber(), booking.getFareClass(), booking.isBaggageIncluded(), booking.getPrice(),
                booking.getStatus(), booking.getUpdatedAt(), booking.getId());
        if (rows == 0) throw EntityNotFoundException.booking(booking.getId());
        return booking;
    }

    @Override
    public boolean deleteById(Long id) {
        return executeUpdate(DELETE, "Failed to delete booking", id) > 0;
    }

    @Override
    public long count() {
        return queryForLong(COUNT, "Failed to count bookings");
    }

    @Override
    public Optional<Booking> findByBookingRef(String bookingRef) {
        return queryForObject(FIND_BY_REF, BookingRepositoryImpl::map,
                "Failed to find booking by reference", bookingRef);
    }

    @Override
    public boolean existsByBookingRef(String bookingRef) {
        return queryForLong(COUNT_BY_REF, "Failed to check booking reference", bookingRef) > 0;
    }

    @Override
    public List<Booking> findByPassengerLastNameContainingIgnoreCase(String lastNamePart) {
        return queryForList(FIND_BY_LAST_NAME, BookingRepositoryImpl::map,
                "Failed to find bookings by passenger last name", containsPattern(lastNamePart));
    }

    @Override
    public List<Booking> findByPassengerDocumentNumber(String documentNumber) {
        return queryForList(FIND_BY_DOCUMENT_NUMBER, BookingRepositoryImpl::map,
                "Failed to find bookings by document number", documentNumber);
    }

    @Override
    public List<Booking> findByFlightNumber(String flightNumber) {
        return queryForList(FIND_BY_FLIGHT_NUMBER, BookingRepositoryImpl::map,
                "Failed to find bookings by flight number", flightNumber);
    }

    @Override
    public List<Booking> findActiveByFlightId(long flightId) {
        return queryForList(FIND_ACTIVE_BY_FLIGHT, BookingRepositoryImpl::map,
                "Failed to find active bookings of flight", flightId, BookingStatus.CANCELLED, BookingStatus.EXPIRED);
    }

    @Override
    public Optional<Booking> findActiveByFlightIdAndPassengerId(long flightId, long passengerId) {
        return queryForObject(FIND_ACTIVE_BY_FLIGHT_AND_PASSENGER, BookingRepositoryImpl::map,
                "Failed to find active booking of passenger on flight",
                flightId, passengerId, BookingStatus.CANCELLED, BookingStatus.EXPIRED);
    }

    @Override
    public Map<Long, Integer> countActiveGroupByFlightId() {
        return queryForList(COUNT_ACTIVE_GROUP_BY_FLIGHT, rs -> Map.entry(rs.getLong(1), rs.getInt(2)),
                "Failed to count active bookings by flight", BookingStatus.CANCELLED, BookingStatus.EXPIRED)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public long countActive() {
        return queryForLong(COUNT_ACTIVE, "Failed to count active bookings",
                BookingStatus.CANCELLED, BookingStatus.EXPIRED);
    }

    @Override
    public long countByPassengerId(long passengerId) {
        return queryForLong(COUNT_BY_PASSENGER, "Failed to count bookings of passenger", passengerId);
    }

    @Override
    public long countByFlightId(long flightId) {
        return queryForLong(COUNT_BY_FLIGHT, "Failed to count bookings of flight", flightId);
    }

    @Override
    public int expireCreatedBefore(LocalDateTime threshold, LocalDateTime now) {
        return executeUpdate(EXPIRE, "Failed to expire unpaid bookings",
                BookingStatus.EXPIRED, now, BookingStatus.CREATED, threshold);
    }

    private static Booking map(ResultSet rs) throws SQLException {
        Booking booking = new Booking(
                rs.getLong("id"),
                rs.getString("booking_ref"),
                PassengerRepositoryImpl.map(rs, PASSENGER_PREFIX),
                FlightRepositoryImpl.map(rs, FLIGHT_PREFIX),
                rs.getString("seat_number"),
                FareClass.valueOf(rs.getString("fare_class")),
                rs.getBoolean("baggage_included"),
                rs.getBigDecimal("price"),
                BookingStatus.valueOf(rs.getString("status")));
        booking.restoreAudit(dateTime(rs, "created_at"), dateTime(rs, "updated_at"));
        return booking;
    }

    private static String containsPattern(String value) {
        // Аналог ILIKE '%…%', переносимый на MySQL (NFR-21): символы %, _ и \ из ввода экранируются.
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
