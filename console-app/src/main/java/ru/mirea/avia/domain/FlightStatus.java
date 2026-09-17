package ru.mirea.avia.domain;

/** Статус рейса: доступность для продажи (BR-01) и допустимые изменения расписания. */
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

    /** На рейс можно оформлять брони только в статусах SCHEDULED и DELAYED (BR-01). */
    public boolean isOpenForSale() {
        return this == SCHEDULED || this == DELAYED;
    }

    /** Рейс уже вылетел или выполнен. */
    public boolean hasDeparted() {
        return this == DEPARTED || this == ARRIVED;
    }

    public boolean isFinal() {
        return this == ARRIVED || this == CANCELLED;
    }

    /** Проверяет допустимость изменения статуса рейса. */
    public boolean canTransitionTo(FlightStatus target) {
        return switch (this) {
            case SCHEDULED -> target == DELAYED || target == DEPARTED || target == CANCELLED;
            case DELAYED -> target == SCHEDULED || target == DEPARTED || target == CANCELLED;
            case DEPARTED -> target == ARRIVED;
            case ARRIVED, CANCELLED -> false;
        };
    }

    public String getTitle() { return title; }
}
