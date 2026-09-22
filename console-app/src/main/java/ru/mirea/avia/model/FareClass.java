package ru.mirea.avia.model;

/** Класс обслуживания. */
public enum FareClass {
    ECONOMY("Эконом"),
    COMFORT("Комфорт"),
    BUSINESS("Бизнес");

    private final String title;

    FareClass(String title) {
        this.title = title;
    }

    public String getTitle() { return title; }
}
