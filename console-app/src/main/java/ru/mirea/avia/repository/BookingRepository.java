package ru.mirea.avia.repository;

import ru.mirea.avia.model.Booking;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Хранилище бронирований — основной сущности системы. */
public interface BookingRepository {
    Booking create(Booking booking) throws SQLException;
    List<Booking> findAll() throws SQLException;
    Optional<Booking> findById(long id) throws SQLException;
    void update(Booking booking) throws SQLException;
    boolean deleteById(long id) throws SQLException;
    List<Booking> searchByPassengerLastName(String text) throws SQLException;
    List<Booking> searchByFlightNumber(String text) throws SQLException;

    int expireCreatedBefore(LocalDateTime threshold) throws SQLException;
}
