package ru.mirea.avia.error;

import java.sql.SQLException;

/**
 * Сигнал ошибки работы с PostgreSQL (коды E-5xx).
 *
 * <p>Оборачивает {@link SQLException}, чтобы оно не выходило за пределы слоя доступа
 * к данным (DB-05). Сообщение внутреннее и английское: оператору текст подбирает
 * {@link ConsoleErrorHandler} по коду ошибки.</p>
 */
public class DataAccessException extends AppException {
    public DataAccessException(ErrorCode code, String message, Throwable cause) { super(code, message, cause); }

    /** Возвращает SQLState исходной ошибки СУБД или {@code null}. */
    public String getSqlState() {
        return getCause() instanceof SQLException sql ? sql.getSQLState() : null;
    }
}
