package ru.mirea.avia.model;

import java.math.BigDecimal;

/** Класс обслуживания и его коэффициент к базовому тарифу рейса. */
public enum FareClass {
    ECONOMY("Эконом", "1.0"),
    COMFORT("Комфорт", "1.6"),
    BUSINESS("Бизнес", "2.5");

    private final String title;
    private final BigDecimal coefficient;

    FareClass(String title, String coefficient) {
        this.title = title;
        this.coefficient = new BigDecimal(coefficient);
    }

    public String getTitle() { return title; }
    public BigDecimal getCoefficient() { return coefficient; }
}
