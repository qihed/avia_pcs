package ru.mirea.avia.repository;

import ru.mirea.avia.model.DocumentType;
import ru.mirea.avia.model.Passenger;
import ru.mirea.avia.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Хранилище пассажиров. */
public class PassengerRepository {
    private final DatabaseManager database;

    public PassengerRepository(DatabaseManager database) {
        this.database = database;
    }

    public Passenger create(Passenger passenger) throws SQLException {
        String sql = """
                INSERT INTO passengers (last_name, first_name, middle_name, birth_date,
                                        document_type, document_number, email, phone)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id, created_at
                """;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passenger.getLastName());
            statement.setString(2, passenger.getFirstName());
            statement.setString(3, passenger.getMiddleName());
            statement.setDate(4, java.sql.Date.valueOf(passenger.getBirthDate()));
            statement.setString(5, passenger.getDocumentType().name());
            statement.setString(6, passenger.getDocumentNumber());
            statement.setString(7, passenger.getEmail());
            statement.setString(8, passenger.getPhone());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                passenger.setId(rs.getLong("id"));
                passenger.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                return passenger;
            }
        }
    }

    public List<Passenger> findAll() throws SQLException {
        String sql = "SELECT * FROM passengers ORDER BY id";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<Passenger> result = new ArrayList<>();
            while (rs.next()) result.add(read(rs));
            return result;
        }
    }

    public Optional<Passenger> findById(long id) throws SQLException {
        String sql = "SELECT * FROM passengers WHERE id = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        }
    }

    public long count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM passengers";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private Passenger read(ResultSet rs) throws SQLException {
        return new Passenger(
                rs.getLong("id"),
                rs.getString("last_name"),
                rs.getString("first_name"),
                rs.getString("middle_name"),
                rs.getDate("birth_date").toLocalDate(),
                DocumentType.valueOf(rs.getString("document_type")),
                rs.getString("document_number"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}
