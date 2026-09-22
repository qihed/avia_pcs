package ru.mirea.avia.exception;

/** Запись с указанным ID не найдена. */
public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}
