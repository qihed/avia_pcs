package ru.mirea.avia.jdbc;

import ru.mirea.avia.error.DataAccessException;
import ru.mirea.avia.error.ErrorCode;

import java.sql.SQLException;

/**
 * Перевод {@link SQLException} в {@link DataAccessException} по классу SQLState.
 *
 * <p>{@code 08…} и {@code 57P…} — нет соединения (E-501), {@code 23…} — нарушение ограничения
 * целостности (E-503), всё остальное — ошибка выполнения запроса (E-502).</p>
 */
public final class SqlErrors {
    private SqlErrors() { }

    public static DataAccessException translate(SQLException ex, String message) {
        String state = ex.getSQLState() == null ? "" : ex.getSQLState();
        if (state.startsWith("08") || state.startsWith("57P")) {
            return new DataAccessException(ErrorCode.E_501, message, ex);
        }
        if (state.startsWith("23")) return new DataAccessException(ErrorCode.E_503, message, ex);
        return new DataAccessException(ErrorCode.E_502, message, ex);
    }
}
