package ru.mirea.avia.support;

import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.repository.BookingRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Репозиторий бронирований в памяти; повторяет семантику SQL-реализации. */
public final class InMemoryBookingRepository implements BookingRepository {
    private static final Comparator<Booking> SEARCH_ORDER =
            Comparator.comparing((Booking b) -> b.getFlight().getDepartureTime()).thenComparing(Booking::getId);

    private final Map<Long, Booking> store = new TreeMap<>();
    private long sequence;

    @Override
    public Booking insert(Booking entity) {
        entity.setId(++sequence);
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public Optional<Booking> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Booking> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public Booking update(Booking entity) {
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public boolean deleteById(Long id) {
        return store.remove(id) != null;
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public Optional<Booking> findByBookingRef(String bookingRef) {
        return where(b -> b.getBookingRef().equals(bookingRef)).stream().findFirst();
    }

    @Override
    public boolean existsByBookingRef(String bookingRef) {
        return findByBookingRef(bookingRef).isPresent();
    }

    @Override
    public List<Booking> findByPassengerLastNameContainingIgnoreCase(String lastNamePart) {
        String part = lastNamePart.toLowerCase(Locale.ROOT);
        return sorted(where(b -> b.getPassenger().getLastName().toLowerCase(Locale.ROOT).contains(part)));
    }

    @Override
    public List<Booking> findByPassengerDocumentNumber(String documentNumber) {
        return sorted(where(b -> b.getPassenger().getDocumentNumber().equals(documentNumber)));
    }

    @Override
    public List<Booking> findByFlightNumber(String flightNumber) {
        return sorted(where(b -> b.getFlight().getFlightNumber().equals(flightNumber)));
    }

    @Override
    public List<Booking> findActiveByFlightId(long flightId) {
        return where(b -> b.getFlight().getId() == flightId && b.isActive());
    }

    @Override
    public Optional<Booking> findActiveByFlightIdAndPassengerId(long flightId, long passengerId) {
        return where(b -> b.getFlight().getId() == flightId && b.getPassenger().getId() == passengerId && b.isActive())
                .stream()
                .findFirst();
    }

    @Override
    public Map<Long, Integer> countActiveGroupByFlightId() {
        return store.values().stream()
                .filter(Booking::isActive)
                .collect(Collectors.groupingBy(b -> b.getFlight().getId(), Collectors.summingInt(b -> 1)));
    }

    @Override
    public long countActive() {
        return where(Booking::isActive).size();
    }

    @Override
    public long countByPassengerId(long passengerId) {
        return where(b -> b.getPassenger().getId() == passengerId).size();
    }

    @Override
    public long countByFlightId(long flightId) {
        return where(b -> b.getFlight().getId() == flightId).size();
    }

    @Override
    public int expireCreatedBefore(LocalDateTime threshold, LocalDateTime now) {
        List<Booking> outdated = where(b -> b.getStatus() == BookingStatus.CREATED && b.getCreatedAt().isBefore(threshold));
        outdated.forEach(b -> b.changeStatus(BookingStatus.EXPIRED, now));
        return outdated.size();
    }

    private List<Booking> where(Predicate<Booking> condition) {
        return store.values().stream().filter(condition).toList();
    }

    private static List<Booking> sorted(List<Booking> bookings) {
        return bookings.stream().sorted(SEARCH_ORDER).toList();
    }
}
