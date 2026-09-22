package ru.mirea.avia.service;

import ru.mirea.avia.exception.BusinessException;
import ru.mirea.avia.exception.EntityNotFoundException;
import ru.mirea.avia.model.Flight;
import ru.mirea.avia.repository.FlightRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Ведение справочника рейсов. */
public class FlightService {
    private final FlightRepository repository;

    public FlightService(FlightRepository repository) {
        this.repository = repository;
    }

    public Flight create(String flightNumber, String airline, String departureAirport, String arrivalAirport,
                         LocalDateTime departureTime, LocalDateTime arrivalTime, String aircraftType,
                         int totalSeats, BigDecimal basePrice) throws SQLException {
        Flight flight = new Flight(flightNumber, airline, departureAirport, arrivalAirport,
                departureTime, arrivalTime, aircraftType, totalSeats, basePrice);
        validate(flight);
        return repository.create(flight);
    }

    public List<Flight> list() throws SQLException {
        return repository.findAll();
    }

    public Flight get(long id) throws SQLException {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Рейс с ID " + id + " не найден"));
    }

    public long count() throws SQLException {
        return repository.findAll().size();
    }

    private void validate(Flight flight) {
        if (flight.getFlightNumber() == null || flight.getFlightNumber().isBlank()) {
            throw new BusinessException("Номер рейса обязателен");
        }
        if (flight.getAirline() == null || flight.getAirline().isBlank()) {
            throw new BusinessException("Авиакомпания обязательна");
        }
        if (flight.getDepartureAirport() == null || flight.getArrivalAirport() == null
                || flight.getDepartureAirport().isBlank() || flight.getArrivalAirport().isBlank()) {
            throw new BusinessException("Аэропорты вылета и прилёта обязательны");
        }
        if (flight.getDepartureAirport().equalsIgnoreCase(flight.getArrivalAirport())) {
            throw new BusinessException("Аэропорт вылета и прилёта не могут совпадать");
        }
        if (flight.getDepartureTime() == null || flight.getArrivalTime() == null
                || !flight.getArrivalTime().isAfter(flight.getDepartureTime())) {
            throw new BusinessException("Время прилёта должно быть позже времени вылета");
        }
        if (flight.getTotalSeats() <= 0) {
            throw new BusinessException("Число мест должно быть больше нуля");
        }
        if (flight.getBasePrice() == null || flight.getBasePrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Базовый тариф должен быть больше нуля");
        }
    }
}
