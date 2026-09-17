package ru.mirea.avia.repository;

import ru.mirea.avia.domain.Booking;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistence gateway for bookings.
 *
 * <p>Bookings are returned fully assembled together with passenger and flight. An "active"
 * booking has any status except CANCELLED and EXPIRED, exactly as in the partial unique indexes.</p>
 */
public interface BookingRepository extends CrudRepository<Booking, Long> {
    Optional<Booking> findByBookingRef(String bookingRef);

    boolean existsByBookingRef(String bookingRef);

    List<Booking> findByPassengerLastNameContainingIgnoreCase(String lastNamePart);

    List<Booking> findByPassengerDocumentNumber(String documentNumber);

    List<Booking> findByFlightNumber(String flightNumber);

    List<Booking> findActiveByFlightId(long flightId);

    Optional<Booking> findActiveByFlightIdAndPassengerId(long flightId, long passengerId);

    /** Returns flight_id to the number of active bookings on that flight. */
    Map<Long, Integer> countActiveGroupByFlightId();

    long countActive();

    long countByPassengerId(long passengerId);

    long countByFlightId(long flightId);

    /** Moves CREATED bookings created before {@code threshold} to EXPIRED (BR-09). */
    int expireCreatedBefore(LocalDateTime threshold, LocalDateTime now);
}
