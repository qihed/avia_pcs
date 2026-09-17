package ru.mirea.avia.error;

/** Сигнал второй активной брони пассажира на тот же рейс — правило BR-04 (код E-303). */
public class DuplicateBookingException extends BusinessRuleException {
    public DuplicateBookingException(String message) { super(ErrorCode.E_303, message); }
}
