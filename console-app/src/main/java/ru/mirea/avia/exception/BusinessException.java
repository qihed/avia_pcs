package ru.mirea.avia.exception;

/** Нарушение бизнес-правила (занятое место, запрещённый переход статуса и т.д.). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
