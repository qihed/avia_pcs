package ru.mirea.avia.error;

/** Сигнал запрещённого перехода статуса — правило BR-07 (код E-302). */
public class InvalidStatusTransitionException extends BusinessRuleException {
    public InvalidStatusTransitionException(String message) { super(ErrorCode.E_302, message); }

    /**
     * Создаёт сообщение вида «Переход COMPLETED -> PAID недопустим».
     *
     * @param from      текущий статус
     * @param to        запрошенный статус
     * @param finalNote пояснение для финального состояния или {@code null}
     * @return исключение с кодом E-302
     */
    public static InvalidStatusTransitionException of(Enum<?> from, Enum<?> to, String finalNote) {
        String message = "Переход " + from.name() + " -> " + to.name() + " недопустим";
        return new InvalidStatusTransitionException(finalNote == null ? message : message + ". " + finalNote);
    }
}
