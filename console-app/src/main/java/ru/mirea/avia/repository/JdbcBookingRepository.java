package ru.mirea.avia.repository;

import ru.mirea.avia.model.Booking;
import ru.mirea.avia.model.BookingStatus;
import ru.mirea.avia.model.DocumentType;
import ru.mirea.avia.model.FareClass;
import ru.mirea.avia.model.Flight;
import ru.mirea.avia.model.FlightStatus;
import ru.mirea.avia.model.Passenger;
import ru.mirea.avia.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Реализация {@link BookingRepository} поверх JDBC (пример полиморфизма). */
public class JdbcBookingRepository implements BookingRepository {
    private static final String SELECT = """
            SELECT b.id, b.seat_number, b.fare_class, b.price, b.status, b.created_at,
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

    private final DatabaseManager database;

    public JdbcBookingRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Booking create(Booking booking) throws SQLException {
        String sql = """
                INSERT INTO bookings (passenger_id, flight_id, seat_number, fare_class, price, status)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id, created_at
                """;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, booking.getPassenger().getId());
            statement.setLong(2, booking.getFlight().getId());
            statement.setString(3, booking.getSeatNumber());
            statement.setString(4, booking.getFareClass().name());
            statement.setBigDecimal(5, booking.getPrice());
            statement.setString(6, booking.getStatus().name());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                booking.setId(rs.getLong("id"));
                booking.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                return booking;
            }
        }
    }

    @Override
    public List<Booking> findAll() throws SQLException {
        String sql = SELECT + "ORDER BY b.id";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return readList(rs);
        }
    }

    @Override
    public Optional<Booking> findById(long id) throws SQLException {
        String sql = SELECT + "WHERE b.id = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public void update(Booking booking) throws SQLException {
        String sql = """
                UPDATE bookings
                SET seat_number = ?, fare_class = ?, price = ?, status = ?
                WHERE id = ?
                """;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, booking.getSeatNumber());
            statement.setString(2, booking.getFareClass().name());
            statement.setBigDecimal(3, booking.getPrice());
            statement.setString(4, booking.getStatus().name());
            statement.setLong(5, booking.getId());
            statement.executeUpdate();
        }
    }

    @Override
    public boolean deleteById(long id) throws SQLException {
        String sql = "DELETE FROM bookings WHERE id = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public int expireCreatedBefore(LocalDateTime threshold) throws SQLException {
        // Один UPDATE на все просроченные брони, статусы передаются параметрами.
        String sql = "UPDATE bookings SET status = ? WHERE status = ? AND created_at < ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, BookingStatus.EXPIRED.name());
            statement.setString(2, BookingStatus.CREATED.name());
            statement.setTimestamp(3, Timestamp.valueOf(threshold));
            return statement.executeUpdate();
        }
    }

    @Override
    public List<Booking> searchByPassengerLastName(String text) throws SQLException {
        String sql = SELECT + "WHERE lower(p.last_name) LIKE lower(?) ORDER BY b.id";
        return search(sql, "%" + text + "%");
    }

    @Override
    public List<Booking> searchByFlightNumber(String text) throws SQLException {
        String sql = SELECT + "WHERE lower(f.flight_number) LIKE lower(?) ORDER BY b.id";
        return search(sql, "%" + text + "%");
    }

    private List<Booking> search(String sql, String pattern) throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            try (ResultSet rs = statement.executeQuery()) {
                return readList(rs);
            }
        }
    }

    private List<Booking> readList(ResultSet rs) throws SQLException {
        List<Booking> result = new ArrayList<>();
        while (rs.next()) result.add(read(rs));
        return result;
    }

    private Booking read(ResultSet rs) throws SQLException {
        Passenger passenger = new Passenger(
                rs.getLong("p_id"), rs.getString("p_last_name"), rs.getString("p_first_name"),
                rs.getString("p_middle_name"), rs.getDate("p_birth_date").toLocalDate(),
                DocumentType.valueOf(rs.getString("p_document_type")), rs.getString("p_document_number"),
                rs.getString("p_email"), rs.getString("p_phone"),
                rs.getTimestamp("p_created_at").toLocalDateTime());
        Flight flight = new Flight(
                rs.getLong("f_id"), rs.getString("f_flight_number"), rs.getString("f_airline"),
                rs.getString("f_departure_airport"), rs.getString("f_arrival_airport"),
                rs.getTimestamp("f_departure_time").toLocalDateTime(),
                rs.getTimestamp("f_arrival_time").toLocalDateTime(), rs.getString("f_aircraft_type"),
                rs.getInt("f_total_seats"), rs.getBigDecimal("f_base_price"),
                FlightStatus.valueOf(rs.getString("f_status")));
        return new Booking(
                rs.getLong("id"), passenger, flight, rs.getString("seat_number"),
                FareClass.valueOf(rs.getString("fare_class")), rs.getBigDecimal("price"),
                BookingStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toLocalDateTime());
    }
}
