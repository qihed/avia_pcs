package ru.mirea.avia.error;

/** Сигнал занятого места или отсутствия свободных мест — правила BR-02 и BR-03 (код E-301). */
public class SeatUnavailableException extends BusinessRuleException {
    public SeatUnavailableException(String message) { super(ErrorCode.E_301, message); }
}
