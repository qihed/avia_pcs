package ru.mirea.avia.repository;

import java.util.List;
import java.util.Optional;

/**
 * Generic persistence gateway: the basic CRUD operations of an entity.
 *
 * <p>Method names follow Spring Data. Instead of {@code save} there are explicit
 * {@link #insert(Object)} and {@link #update(Object)}, because plain JDBC has no dirty checking.</p>
 *
 * @param <T>  entity type
 * @param <ID> identifier type
 */
public interface CrudRepository<T, ID> {
    /** Inserts a new entity and returns it with the generated identifier. */
    T insert(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();

    /** Saves the changes of an existing entity. */
    T update(T entity);

    /** Deletes the record; returns {@code true} when the record existed. */
    boolean deleteById(ID id);

    long count();
}
