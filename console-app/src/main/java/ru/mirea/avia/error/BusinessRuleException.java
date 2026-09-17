package ru.mirea.avia.error;

/** Сигнал нарушения бизнес-правила BR-01…BR-10 (коды E-3xx). */
public class BusinessRuleException extends AppException {
    public BusinessRuleException(ErrorCode code, String message) { super(code, message); }
}
