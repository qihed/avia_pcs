package ru.mirea.avia.model;

/** Статус рейса. */
public enum FlightStatus {
    SCHEDULED("По расписанию"),
    DELAYED("Задержан"),
    DEPARTED("Вылетел"),
    ARRIVED("Прибыл"),
    CANCELLED("Отменён");

    private final String title;

    FlightStatus(String title) {
        this.title = title;
    }

    /** На рейс можно оформлять брони только пока он не вылетел и не отменён. */
    public boolean isOpenForSale() {
        return this == SCHEDULED || this == DELAYED;
    }

    public String getTitle() { return title; }
}
