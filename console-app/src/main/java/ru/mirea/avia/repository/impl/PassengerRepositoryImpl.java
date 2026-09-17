package ru.mirea.avia.repository.impl;

import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.domain.Passenger;
import ru.mirea.avia.error.EntityNotFoundException;
import ru.mirea.avia.jdbc.DatabaseManager;
import ru.mirea.avia.repository.PassengerRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Persistence gateway for passengers. */
public final class PassengerRepositoryImpl extends AbstractJdbcRepository implements PassengerRepository {
    private static final String SELECT = """
            SELECT id, last_name, first_name, middle_name, birth_date, document_type,
                   document_number, email, phone, created_at
            FROM passengers
            """;
    private static final String FIND_BY_ID = SELECT + "WHERE id = ?";
    private static final String FIND_ALL = SELECT + "ORDER BY id";
    private static final String FIND_BY_DOCUMENT = SELECT + "WHERE document_type = ? AND document_number = ?";
    private static final String FIND_BY_EMAIL = SELECT + "WHERE LOWER(email) = LOWER(?)";
    private static final String INSERT = """
            INSERT INTO passengers (last_name, first_name, middle_name, birth_date,
                                    document_type, document_number, email, phone, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE = """
            UPDATE passengers
            SET last_name = ?, first_name = ?, middle_name = ?, birth_date = ?,
                document_type = ?, document_number = ?, email = ?, phone = ?
            WHERE id = ?
            """;
    private static final String DELETE = "DELETE FROM passengers WHERE id = ?";
    private static final String COUNT = "SELECT count(*) FROM passengers";
    private static final String NO_PREFIX = "";

    public PassengerRepositoryImpl(DatabaseManager database) {
        super(database);
    }

    @Override
    public Passenger insert(Passenger passenger) {
        long id = insertAndGetId(INSERT, "Failed to insert passenger",
                passenger.getLastName(), passenger.getFirstName(), passenger.getMiddleName(),
                passenger.getBirthDate(), passenger.getDocumentType(), passenger.getDocumentNumber(),
                passenger.getEmail(), passenger.getPhone(), passenger.getCreatedAt());
        passenger.setId(id);
        return passenger;
    }

    @Override
    public Optional<Passenger> findById(Long id) {
        return queryForObject(FIND_BY_ID, rs -> map(rs, NO_PREFIX), "Failed to find passenger by id", id);
    }

    @Override
    public List<Passenger> findAll() {
        return queryForList(FIND_ALL, rs -> map(rs, NO_PREFIX), "Failed to list passengers");
    }

    @Override
    public Passenger update(Passenger passenger) {
        int rows = executeUpdate(UPDATE, "Failed to update passenger",
                passenger.getLastName(), passenger.getFirstName(), passenger.getMiddleName(),
                passenger.getBirthDate(), passenger.getDocumentType(), passenger.getDocumentNumber(),
                passenger.getEmail(), passenger.getPhone(), passenger.getId());
        if (rows == 0) throw EntityNotFoundException.passenger(passenger.getId());
        return passenger;
    }

    @Override
    public boolean deleteById(Long id) {
        return executeUpdate(DELETE, "Failed to delete passenger", id) > 0;
    }

    @Override
    public long count() {
        return queryForLong(COUNT, "Failed to count passengers");
    }

    @Override
    public Optional<Passenger> findByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber) {
        return queryForObject(FIND_BY_DOCUMENT, rs -> map(rs, NO_PREFIX),
                "Failed to find passenger by document", documentType, documentNumber);
    }

    @Override
    public Optional<Passenger> findByEmailIgnoreCase(String email) {
        return queryForObject(FIND_BY_EMAIL, rs -> map(rs, NO_PREFIX), "Failed to find passenger by email", email);
    }

    /**
     * Reads a passenger from the current row.
     *
     * <p>{@code prefix} is empty for the {@code passengers} table and {@code p_} for the booking
     * join, where columns are aliased.</p>
     */
    static Passenger map(ResultSet rs, String prefix) throws SQLException {
        Passenger passenger = new Passenger(
                rs.getString(prefix + "last_name"),
                rs.getString(prefix + "first_name"),
                date(rs, prefix + "birth_date"),
                DocumentType.valueOf(rs.getString(prefix + "document_type")),
                rs.getString(prefix + "document_number"),
                rs.getString(prefix + "email"),
                rs.getString(prefix + "phone"));
        passenger.setId(rs.getLong(prefix + "id"));
        passenger.setMiddleName(rs.getString(prefix + "middle_name"));
        passenger.setCreatedAt(dateTime(rs, prefix + "created_at"));
        return passenger;
    }
}
