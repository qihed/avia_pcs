package ru.mirea.avia.support;

import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.domain.Passenger;
import ru.mirea.avia.repository.PassengerRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Репозиторий пассажиров в памяти для проверки бизнес-правил без PostgreSQL. */
public final class InMemoryPassengerRepository implements PassengerRepository {
    private final Map<Long, Passenger> store = new TreeMap<>();
    private long sequence;

    @Override
    public Passenger insert(Passenger entity) {
        entity.setId(++sequence);
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public Optional<Passenger> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Passenger> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Passenger update(Passenger entity) {
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
    public Optional<Passenger> findByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber) {
        return store.values().stream()
                .filter(p -> p.getDocumentType() == documentType && p.getDocumentNumber().equals(documentNumber))
                .findFirst();
    }

    @Override
    public Optional<Passenger> findByEmailIgnoreCase(String email) {
        String normalized = email.toLowerCase(Locale.ROOT);
        return store.values().stream()
                .filter(p -> p.getEmail().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}
