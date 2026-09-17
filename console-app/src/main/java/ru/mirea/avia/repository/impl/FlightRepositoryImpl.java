package ru.mirea.avia.repository.impl;

import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.domain.FlightStatus;
import ru.mirea.avia.error.EntityNotFoundException;
import ru.mirea.avia.jdbc.DatabaseManager;
import ru.mirea.avia.repository.FlightRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence gateway for flights. */
public final class FlightRepositoryImpl extends AbstractJdbcRepository implements FlightRepository {
    private static final String SELECT = """
            SELECT id, flight_number, airline, departure_airport, arrival_airport, departure_time,
                   arrival_time, aircraft_type, total_seats, base_price, status
            FROM flights
            """;
    private static final String FIND_BY_ID = SELECT + "WHERE id = ?";
    private static final String FIND_ALL = SELECT + "ORDER BY departure_time, id";
    private static final String FIND_BY_NUMBER_AND_DEPARTURE = SELECT
            + "WHERE flight_number = ? AND departure_time = ?";
    private static final String INSERT = """
            INSERT INTO flights (flight_number, airline, departure_airport, arrival_airport,
                                 departure_time, arrival_time, aircraft_type, total_seats, base_price, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE = """
            UPDATE flights
            SET flight_number = ?, airline = ?, departure_airport = ?, arrival_airport = ?,
                departure_time = ?, arrival_time = ?, aircraft_type = ?, total_seats = ?,
                base_price = ?, status = ?
            WHERE id = ?
            """;
    private static final String DELETE = "DELETE FROM flights WHERE id = ?";
    private static final String COUNT = "SELECT count(*) FROM flights";
    private static final String NO_PREFIX = "";

    public FlightRepositoryImpl(DatabaseManager database) {
        super(database);
    }

    @Override
    public Flight insert(Flight flight) {
        long id = insertAndGetId(INSERT, "Failed to insert flight",
                flight.getFlightNumber(), flight.getAirline(), flight.getDepartureAirport(),
                flight.getArrivalAirport(), flight.getDepartureTime(), flight.getArrivalTime(),
                flight.getAircraftType(), flight.getTotalSeats(), flight.getBasePrice(), flight.getStatus());
        flight.setId(id);
        return flight;
    }

    @Override
    public Optional<Flight> findById(Long id) {
        return queryForObject(FIND_BY_ID, rs -> map(rs, NO_PREFIX), "Failed to find flight by id", id);
    }

    @Override
    public List<Flight> findAll() {
        return queryForList(FIND_ALL, rs -> map(rs, NO_PREFIX), "Failed to list flights");
    }

    @Override
    public Flight update(Flight flight) {
        int rows = executeUpdate(UPDATE, "Failed to update flight",
                flight.getFlightNumber(), flight.getAirline(), flight.getDepartureAirport(),
                flight.getArrivalAirport(), flight.getDepartureTime(), flight.getArrivalTime(),
                flight.getAircraftType(), flight.getTotalSeats(), flight.getBasePrice(), flight.getStatus(),
                flight.getId());
        if (rows == 0) throw EntityNotFoundException.flight(flight.getId());
        return flight;
    }

    @Override
    public boolean deleteById(Long id) {
        return executeUpdate(DELETE, "Failed to delete flight", id) > 0;
    }

    @Override
    public long count() {
        return queryForLong(COUNT, "Failed to count flights");
    }

    @Override
    public Optional<Flight> findByFlightNumberAndDepartureTime(String flightNumber, LocalDateTime departureTime) {
        return queryForObject(FIND_BY_NUMBER_AND_DEPARTURE, rs -> map(rs, NO_PREFIX),
                "Failed to find flight by number and departure time", flightNumber, departureTime);
    }

    /**
     * Reads a flight from the current row.
     *
     * <p>{@code prefix} is empty for the {@code flights} table and {@code f_} for the booking
     * join, where columns are aliased.</p>
     */
    static Flight map(ResultSet rs, String prefix) throws SQLException {
        Flight flight = new Flight(
                rs.getString(prefix + "flight_number"),
                rs.getString(prefix + "airline"),
                rs.getString(prefix + "departure_airport"),
                rs.getString(prefix + "arrival_airport"),
                dateTime(rs, prefix + "departure_time"),
                dateTime(rs, prefix + "arrival_time"),
                rs.getString(prefix + "aircraft_type"),
                rs.getInt(prefix + "total_seats"),
                rs.getBigDecimal(prefix + "base_price"));
        flight.setId(rs.getLong(prefix + "id"));
        flight.setStatus(FlightStatus.valueOf(rs.getString(prefix + "status")));
        return flight;
    }
}
