package ru.mirea.avia.repository;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import ru.mirea.avia.domain.Booking;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
    "update Booking b set b.status=ru.mirea.avia.domain.BookingStatus.EXPIRED, b.version=b.version+1, b.updatedAt=:now where b.status=ru.mirea.avia.domain.BookingStatus.CREATED and b.createdAt<=:cutoff"
  )
  int expireBefore(java.time.LocalDateTime cutoff, java.time.LocalDateTime now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Booking e where e.id = :id")
  Optional<Booking> lockById(UUID id);

  List<Booking> findByFlightId(UUID flightId);
  boolean existsByPassengerId(UUID passengerId);
  boolean existsByFlightId(UUID flightId);
  boolean existsByBookingCode(String code);
  org.springframework.data.domain.Page<Booking> findByArchived(
    boolean archived,
    org.springframework.data.domain.Pageable pageable
  );
}
