package ru.mirea.avia.model;

/** Статус бронирования и допустимые переходы между статусами. */
public enum BookingStatus {
    CREATED("Создано, ожидает оплаты", "Создано"),
    PAID("Оплачено", "Оплачено"),
    CHECKED_IN("Регистрация пройдена", "Регистрация"),
    COMPLETED("Перелёт выполнен", "Выполнено"),
    CANCELLED("Отменено", "Отменено");

    private final String title;
    private final String shortTitle;

    BookingStatus(String title, String shortTitle) {
        this.title = title;
        this.shortTitle = shortTitle;
    }

    /** Проверяет допустимость перехода в новый статус. */
    public boolean canChangeTo(BookingStatus target) {
        return switch (this) {
            case CREATED -> target == PAID || target == CANCELLED;
            case PAID -> target == CHECKED_IN || target == CANCELLED;
            case CHECKED_IN -> target == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /** Бронь ещё занимает место на рейсе (не отменена). */
    public boolean occupiesSeat() {
        return this != CANCELLED;
    }

    public String getTitle() { return title; }
    public String getShortTitle() { return shortTitle; }
}
