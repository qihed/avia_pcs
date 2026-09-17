package ru.mirea.avia.error;

/** Сигнал некорректного ввода или формата данных (коды E-1xx). */
public class ValidationException extends AppException {
    public ValidationException(ErrorCode code, String message) { super(code, message); }
}
