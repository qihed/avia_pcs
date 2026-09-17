package ru.mirea.avia.error;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.dto.ErrorReport;
import ru.mirea.avia.jdbc.SqlErrors;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет, что оператор видит код ошибки и безопасный текст без подробностей СУБД (FR-02). */
class ConsoleErrorHandlerTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);
    private final ConsoleErrorHandler errors = new ConsoleErrorHandler(clock);

    @Test
    void keepsApplicationErrorCodeAndMessage() {
        ErrorReport seat = errors.handle(
                new SeatUnavailableException("Место 12A на рейсе SU1420 уже занято"), "bookings.create");
        ErrorReport missing = errors.handle(EntityNotFoundException.booking(99), "bookings.get");

        assertThat(seat.code()).isEqualTo("E-301");
        assertThat(seat.message()).isEqualTo("Место 12A на рейсе SU1420 уже занято");
        assertThat(seat.command()).isEqualTo("bookings.create");
        assertThat(seat.timestamp()).isEqualTo(clock.instant());
        assertThat(missing.code()).isEqualTo("E-201");
        assertThat(missing.message()).isEqualTo("Бронирование с ID 99 не найдено");
    }

    @Test
    void keepsValidationMessageWithoutTrailingPeriod() {
        ErrorReport report = errors.handle(
                new ValidationException(ErrorCode.E_104, "Фамилия не может быть пустой"), "passengers.create");

        assertThat(report.code()).isEqualTo("E-104");
        assertThat(report.message()).isEqualTo("Фамилия не может быть пустой").doesNotEndWith(".");
    }

    @Test
    void hidesConstraintNameOnIntegrityViolation() {
        SQLException cause = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"uq_bookings_ref\"", "23505");

        ErrorReport report = errors.handle(SqlErrors.translate(cause, "Failed to insert booking"), "bookings.create");

        assertThat(report.code()).isEqualTo("E-503");
        assertThat(report.message())
                .isEqualTo("Операция нарушает целостность данных и была отклонена")
                .doesNotContain("uq_bookings_ref")
                .doesNotContain("23505");
    }

    @Test
    void reportsLostConnection() {
        SQLException cause = new SQLException("Connection to localhost:5432 refused", "08001");

        ErrorReport report = errors.handle(SqlErrors.translate(cause, "Failed to load bookings"), "bookings.list");

        assertThat(report.code()).isEqualTo("E-501");
        assertThat(report.message())
                .isEqualTo("Нет связи с базой данных. Проверьте, запущен ли сервер PostgreSQL")
                .doesNotContain("localhost");
    }

    @Test
    void hidesSqlDetailsOnQueryFailure() {
        SQLException cause = new SQLException("ERROR: relation \"avia.bookings\" does not exist", "42P01");

        ErrorReport report = errors.handle(SqlErrors.translate(cause, "Failed to load bookings"), "bookings.list");

        assertThat(report.code()).isEqualTo("E-502");
        assertThat(report.message())
                .isEqualTo("Ошибка обращения к данным, операция отменена")
                .doesNotContain("avia.bookings")
                .doesNotContain("Failed to load bookings");
    }

    @Test
    void mapsUnknownErrorToInternalError() {
        ErrorReport report = errors.handle(
                new IllegalStateException("Cannot invoke Flight.getId() because flight is null"), "statistics.show");

        assertThat(report.code()).isEqualTo("E-500");
        assertThat(report.message())
                .isEqualTo("Внутренняя ошибка. Сообщите администратору correlation ID")
                .doesNotContain("Flight.getId()");
        assertThat(report.command()).isEqualTo("statistics.show");
    }

    @Test
    void attachesCommandCorrelationId() {
        CorrelationId.restore("3f2b9c1e-7a4d-4e21-9d0c-5b6a7e8f9a01");
        try {
            ErrorReport report = errors.handle(new IllegalStateException("Unexpected state"), "statistics.show");

            assertThat(report.correlationId()).isEqualTo("3f2b9c1e-7a4d-4e21-9d0c-5b6a7e8f9a01");
        } finally {
            CorrelationId.restore(null);
        }
    }
}
