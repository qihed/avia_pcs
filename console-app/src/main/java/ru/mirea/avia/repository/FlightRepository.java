package ru.mirea.avia.repository;

import ru.mirea.avia.model.Flight;
import ru.mirea.avia.model.FlightStatus;
import ru.mirea.avia.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Хранилище рейсов. */
public class FlightRepository {
    private final DatabaseManager database;

    public FlightRepository(DatabaseManager database) {
        this.database = database;
    }

    public Flight create(Flight flight) throws SQLException {
        String sql = """
                INSERT INTO flights (flight_number, airline, departure_airport, arrival_airport,
                                     departure_time, arrival_time, aircraft_type, total_seats,
                                     base_price, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, flight.getFlightNumber());
            statement.setString(2, flight.getAirline());
            statement.setString(3, flight.getDepartureAirport());
            statement.setString(4, flight.getArrivalAirport());
            statement.setTimestamp(5, Timestamp.valueOf(flight.getDepartureTime()));
            statement.setTimestamp(6, Timestamp.valueOf(flight.getArrivalTime()));
            statement.setString(7, flight.getAircraftType());
            statement.setInt(8, flight.getTotalSeats());
            statement.setBigDecimal(9, flight.getBasePrice());
            statement.setString(10, flight.getStatus().name());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                flight.setId(rs.getLong("id"));
                return flight;
            }
        }
    }

    public List<Flight> findAll() throws SQLException {
        String sql = "SELECT * FROM flights ORDER BY id";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<Flight> result = new ArrayList<>();
            while (rs.next()) result.add(read(rs));
            return result;
        }
    }

    public Optional<Flight> findById(long id) throws SQLException {
        String sql = "SELECT * FROM flights WHERE id = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        }
    }

    private Flight read(ResultSet rs) throws SQLException {
        return new Flight(
                rs.getLong("id"),
                rs.getString("flight_number"),
                rs.getString("airline"),
                rs.getString("departure_airport"),
                rs.getString("arrival_airport"),
                rs.getTimestamp("departure_time").toLocalDateTime(),
                rs.getTimestamp("arrival_time").toLocalDateTime(),
                rs.getString("aircraft_type"),
                rs.getInt("total_seats"),
                rs.getBigDecimal("base_price"),
                FlightStatus.valueOf(rs.getString("status")));
    }
}
