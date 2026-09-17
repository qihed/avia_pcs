package ru.mirea.avia.repository;

import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.domain.Passenger;

import java.util.Optional;

/** Persistence gateway for passengers. */
public interface PassengerRepository extends CrudRepository<Passenger, Long> {
    Optional<Passenger> findByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

    Optional<Passenger> findByEmailIgnoreCase(String email);
}
