package ru.mirea.avia.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Статус бронирования и матрица допустимых переходов (п. 5.4 ТЗ, правило BR-07).
 *
 * <p>Матрица реализована непосредственно в перечислении, поэтому логика переходов
 * не дублируется в сервисах.</p>
 */
public enum BookingStatus {
    CREATED("Создано, ожидает оплаты", "Создано"),
    PAID("Оплачено", "Оплачено"),
    CHECKED_IN("Регистрация пройдена", "Регистрация"),
    COMPLETED("Перелёт выполнен", "Выполнено"),
    CANCELLED("Отменено", "Отменено"),
    EXPIRED("Бронь просрочена", "Просрочено");

    private final String title;
    private final String shortTitle;

    BookingStatus(String title, String shortTitle) {
        this.title = title;
        this.shortTitle = shortTitle;
    }

    /** Финальные состояния: из них переходы запрещены. */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED || this == EXPIRED;
    }

    /**
     * Занимает ли бронь место на рейсе.
     *
     * <p>Условие совпадает с частичными уникальными индексами
     * {@code uq_bookings_flight_seat_active} и {@code uq_bookings_flight_passenger_active}
     * (правила BR-03 и BR-04).</p>
     */
    public boolean occupiesSeat() {
        return this != CANCELLED && this != EXPIRED;
    }

    /** Бронь «в работе» для фильтра FL-01 «Только активные»: CREATED, PAID, CHECKED_IN. */
    public boolean isInProgress() {
        return this == CREATED || this == PAID || this == CHECKED_IN;
    }

    /** Оплаченные брони учитываются в выручке (ST-06, ST-07). */
    public boolean isPaid() {
        return this == PAID || this == CHECKED_IN || this == COMPLETED;
    }

    /** Проверяет допустимость перехода по матрице п. 5.4.2 ТЗ. */
    public boolean canTransitionTo(BookingStatus target) {
        return switch (this) {
            case CREATED -> target == PAID || target == CANCELLED || target == EXPIRED;
            case PAID -> target == CHECKED_IN || target == CANCELLED;
            case CHECKED_IN -> target == COMPLETED;
            case COMPLETED, CANCELLED, EXPIRED -> false;
        };
    }

    /** Возвращает все статусы, в которые разрешён переход из текущего. */
    public List<BookingStatus> allowedTransitions() {
        return Arrays.stream(values())
                .filter(this::canTransitionTo)
                .toList();
    }

    public String getTitle() { return title; }
    public String getShortTitle() { return shortTitle; }
}
