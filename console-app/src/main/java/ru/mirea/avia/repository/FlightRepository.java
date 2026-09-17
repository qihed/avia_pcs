package ru.mirea.avia.repository;

import ru.mirea.avia.domain.Flight;

import java.time.LocalDateTime;
import java.util.Optional;

/** Persistence gateway for flights. */
public interface FlightRepository extends CrudRepository<Flight, Long> {
    /** Uses the unique pair "flight number + departure time" (uq_flights_number_date). */
    Optional<Flight> findByFlightNumberAndDepartureTime(String flightNumber, LocalDateTime departureTime);
}
