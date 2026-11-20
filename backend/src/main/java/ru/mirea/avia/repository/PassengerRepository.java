package ru.mirea.avia.repository;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import ru.mirea.avia.domain.Passenger;

public interface PassengerRepository extends JpaRepository<Passenger, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Passenger e where e.id = :id")
  Optional<Passenger> lockById(UUID id);
}
