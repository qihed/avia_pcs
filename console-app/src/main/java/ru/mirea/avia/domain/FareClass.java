package ru.mirea.avia.domain;

import java.math.BigDecimal;

/** Класс обслуживания: тарифный коэффициент и условие провоза багажа (п. 6.4.1 ТЗ, BR-06). */
public enum FareClass {
    ECONOMY("Эконом", new BigDecimal("1.0"), false),
    COMFORT("Комфорт", new BigDecimal("1.6"), false),
    BUSINESS("Бизнес", new BigDecimal("2.5"), true);

    private final String title;
    private final BigDecimal coefficient;
    private final boolean baggageIncluded;

    FareClass(String title, BigDecimal coefficient, boolean baggageIncluded) {
        this.title = title;
        this.coefficient = coefficient;
        this.baggageIncluded = baggageIncluded;
    }

    public String getTitle() { return title; }
    public BigDecimal getCoefficient() { return coefficient; }
    public boolean isBaggageIncluded() { return baggageIncluded; }
}
