package ru.mirea.avia.service;

import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.dto.BookingDtos.PriceQuote;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Единообразно рассчитывает стоимость билета по правилу BR-06 (FR-19). */
public final class FareCalculator {
    public static final BigDecimal BAGGAGE_FEE = new BigDecimal("2500.00");

    /** Возвращает детализацию стоимости билета для базового тарифа, класса обслуживания и багажа. */
    public PriceQuote calculate(BigDecimal basePrice, FareClass fareClass, boolean baggageIncluded) {
        Objects.requireNonNull(basePrice, "basePrice");
        Objects.requireNonNull(fareClass, "fareClass");
        if (basePrice.signum() <= 0) {
            throw new ValidationException(ErrorCode.E_103, "Базовый тариф рейса должен быть больше нуля");
        }
        /*
         * Сбор за багаж платится только тогда, когда багаж заказан отдельно:
         * - эконом и комфорт — 2 500 руб. за заказанный багаж;
         * - бизнес — 0 руб., потому что багаж уже входит в тариф.
         */
        BigDecimal baggageFee = baggageIncluded && !fareClass.isBaggageIncluded()
                ? BAGGAGE_FEE
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        /*
         * Формула BR-06: price = base_price × k(fareClass) + baggageFee.
         * Коэффициенты классов: эконом 1.0, комфорт 1.6, бизнес 2.5.
         * Итог округляется до копеек по правилу HALF_UP, поэтому слишком малый тариф
         * может дать нулевую сумму — такая стоимость отклоняется.
         */
        BigDecimal total = basePrice.multiply(fareClass.getCoefficient())
                .add(baggageFee)
                .setScale(2, RoundingMode.HALF_UP);
        if (total.signum() <= 0) {
            throw new ValidationException(ErrorCode.E_103, "Стоимость билета должна быть больше нуля");
        }
        return new PriceQuote(basePrice, fareClass, fareClass.getCoefficient(), baggageFee, total);
    }
}
