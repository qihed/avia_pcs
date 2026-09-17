package ru.mirea.avia.error;

/**
 * Общий предок прикладных исключений (рис. 6.2 ТЗ).
 *
 * <p>Исключение непроверяемое и хранит код ошибки. Сообщение предназначено оператору
 * и написано по-русски; технические подробности пишутся только в журнал.</p>
 */
public abstract class AppException extends RuntimeException {
    private final ErrorCode code;

    protected AppException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    protected AppException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** Возвращает строковый код ошибки, например {@code E-301}. */
    public String getErrorCode() {
        return code.getCode();
    }

    public ErrorCode getCode() { return code; }
}
