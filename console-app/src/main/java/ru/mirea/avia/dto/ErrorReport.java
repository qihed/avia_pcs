package ru.mirea.avia.dto;

import java.time.Instant;

/**
 * Безопасный отчёт об ошибке команды для вывода в консоль.
 *
 * <p>Не содержит stack trace, SQL и имён классов: технические подробности остаются
 * в журнале и связываются с отчётом через correlation ID.</p>
 */
public record ErrorReport(
        Instant timestamp,
        String code,
        String message,
        String command,
        String correlationId) {
}
