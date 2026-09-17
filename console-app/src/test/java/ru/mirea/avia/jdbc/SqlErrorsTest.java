package ru.mirea.avia.jdbc;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.error.DataAccessException;
import ru.mirea.avia.error.ErrorCode;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет перевод SQLState PostgreSQL в коды ошибок доступа к данным E-501…E-503. */
class SqlErrorsTest {
    @Test
    void mapsConnectionFailuresToUnavailable() {
        assertThat(code("08001")).isEqualTo(ErrorCode.E_501);
        assertThat(code("08006")).isEqualTo(ErrorCode.E_501);
        assertThat(code("08S01")).isEqualTo(ErrorCode.E_501);
        assertThat(code("57P01")).isEqualTo(ErrorCode.E_501);
    }

    @Test
    void mapsIntegrityViolationsToConflict() {
        assertThat(code("23505")).isEqualTo(ErrorCode.E_503);
        assertThat(code("23503")).isEqualTo(ErrorCode.E_503);
        assertThat(code("23514")).isEqualTo(ErrorCode.E_503);
        assertThat(code("23502")).isEqualTo(ErrorCode.E_503);
    }

    @Test
    void mapsOtherStatesToQueryFailure() {
        assertThat(code("42P01")).isEqualTo(ErrorCode.E_502);
        assertThat(code("22001")).isEqualTo(ErrorCode.E_502);
        assertThat(code("40001")).isEqualTo(ErrorCode.E_502);
        assertThat(code("")).isEqualTo(ErrorCode.E_502);
        assertThat(code(null)).isEqualTo(ErrorCode.E_502);
    }

    @Test
    void keepsCauseAndInternalMessage() {
        SQLException cause = new SQLException("duplicate key value violates unique constraint", "23505");

        DataAccessException translated = SqlErrors.translate(cause, "Failed to insert booking");

        assertThat(translated.getMessage()).isEqualTo("Failed to insert booking");
        assertThat(translated.getCause()).isSameAs(cause);
        assertThat(translated.getSqlState()).isEqualTo("23505");
        assertThat(translated.getErrorCode()).isEqualTo("E-503");
    }

    private static ErrorCode code(String sqlState) {
        return SqlErrors.translate(new SQLException("database error", sqlState), "Failed to run query").getCode();
    }
}
