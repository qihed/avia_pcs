package ru.mirea.avia.error;

import java.util.UUID;

/**
 * Correlation ID текущей команды меню.
 *
 * <p>Аналог {@code MDC.put("correlationId", …)} из эталонного {@code CorrelationIdFilter}.
 * Используемый бэкенд журнала {@code slf4j-simple} не поддерживает MDC, поэтому идентификатор
 * хранится в собственном {@link ThreadLocal}: по нему администратор находит в журнале
 * технические подробности ошибки, о которой сообщил оператор.</p>
 */
public final class CorrelationId {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationId() { }

    /** Открывает новый ID команды и возвращает предыдущий, чтобы восстановить его после вложенной команды. */
    public static String open() {
        String previous = CURRENT.get();
        CURRENT.set(UUID.randomUUID().toString());
        return previous;
    }

    /** Возвращает ID текущей команды или {@code null}, если команда не выполняется. */
    public static String current() {
        return CURRENT.get();
    }

    /** Устанавливает ID; {@code null} очищает контекст потока. */
    public static void restore(String correlationId) {
        if (correlationId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(correlationId);
        }
    }
}
