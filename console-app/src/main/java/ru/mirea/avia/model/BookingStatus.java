package ru.mirea.avia.model;

import java.util.Arrays;
import java.util.List;

/** Статус бронирования и допустимые переходы между статусами */
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

    /** Проверяет допустимость перехода в новый статус. */
    public boolean canChangeTo(BookingStatus target) {
        return switch (this) {
            case CREATED -> target == PAID || target == CANCELLED || target == EXPIRED;
            case PAID -> target == CHECKED_IN || target == CANCELLED;
            case CHECKED_IN -> target == COMPLETED;
            case COMPLETED, CANCELLED, EXPIRED -> false;
        };
    }

    /** Статусы, которые оператор может выбрать вручную; EXPIRED ставит только система. */
    public List<BookingStatus> operatorTargets() {
        return Arrays.stream(values())
                .filter(this::canChangeTo)
                .filter(target -> target != EXPIRED)
                .toList();
    }

    /** Бронь ещё занимает место на рейсе: отменённая и просроченная возвращают место в продажу. */
    public boolean occupiesSeat() {
        return this != CANCELLED && this != EXPIRED;
    }

    public String getTitle() { return title; }
    public String getShortTitle() { return shortTitle; }
}
