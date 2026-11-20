package ru.mirea.avia.repository;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import ru.mirea.avia.domain.Flight;

public interface FlightRepository extends JpaRepository<Flight, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Flight e where e.id = :id")
  Optional<Flight> lockById(UUID id);
}
