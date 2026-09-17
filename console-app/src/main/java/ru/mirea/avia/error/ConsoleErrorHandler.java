package ru.mirea.avia.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mirea.avia.dto.ErrorReport;

import java.time.Clock;
import java.time.Instant;

/**
 * Преобразует исключения команд в безопасные отчёты для консоли (FR-02, п. 6.10.2 ТЗ).
 *
 * <p>Оператор видит код ошибки и пояснение на русском языке. Stack trace, SQLState и
 * имена ограничений БД пишутся только в журнал вместе с correlation ID команды.</p>
 */
public final class ConsoleErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ConsoleErrorHandler.class);

    private final Clock clock;

    public ConsoleErrorHandler(Clock clock) {
        this.clock = clock;
    }

    /** Возвращает отчёт об ошибке команды {@code command}. */
    public ErrorReport handle(Throwable ex, String command) {
        String correlationId = CorrelationId.current();
        return switch (ex) {
            case DataAccessException data when data.getCode() == ErrorCode.E_501 -> {
                log.warn("Database unavailable, correlationId={}, command={}", correlationId, command, data);
                yield build(data.getErrorCode(),
                        "Нет связи с базой данных. Проверьте, запущен ли сервер PostgreSQL", command);
            }
            case DataAccessException data when data.getCode() == ErrorCode.E_503 -> {
                log.warn("Integrity violation, sqlState={}, correlationId={}", data.getSqlState(), correlationId, data);
                // Имя SQL constraint не возвращается пользователю, так как раскрывает внутреннюю схему БД.
                yield build(data.getErrorCode(), "Операция нарушает целостность данных и была отклонена", command);
            }
            case DataAccessException data -> {
                log.warn("Data access failed, sqlState={}, correlationId={}", data.getSqlState(), correlationId, data);
                yield build(ErrorCode.E_502.getCode(), "Ошибка обращения к данным, операция отменена", command);
            }
            case AppException app -> build(app.getErrorCode(), app.getMessage(), command);
            default -> {
                log.error("Unhandled error, correlationId={}, command={}", correlationId, command, ex);
                yield build(ErrorCode.E_500.getCode(),
                        "Внутренняя ошибка. Сообщите администратору correlation ID", command);
            }
        };
    }

    private ErrorReport build(String code, String message, String command) {
        return new ErrorReport(Instant.now(clock), code, message, command, CorrelationId.current());
    }
}
