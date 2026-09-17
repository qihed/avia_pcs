package ru.mirea.avia.support;

import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.repository.FlightRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Репозиторий рейсов в памяти для проверки бизнес-правил без PostgreSQL. */
public final class InMemoryFlightRepository implements FlightRepository {
    private final Map<Long, Flight> store = new TreeMap<>();
    private long sequence;

    @Override
    public Flight insert(Flight entity) {
        entity.setId(++sequence);
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public Optional<Flight> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Flight> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(Flight::getDepartureTime).thenComparing(Flight::getId))
                .toList();
    }

    @Override
    public Flight update(Flight entity) {
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
    public Optional<Flight> findByFlightNumberAndDepartureTime(String flightNumber, LocalDateTime departureTime) {
        return store.values().stream()
                .filter(f -> f.getFlightNumber().equals(flightNumber) && f.getDepartureTime().equals(departureTime))
                .findFirst();
    }
}
