package ru.mirea.avia.filter;

import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Критерии отбора бронирований (FL-01…FL-05 и умный фильтр).
 *
 * <p>Поле со значением {@code null} означает «без ограничения». Record неизменяемый:
 * каждый пункт меню фильтрации возвращает новый фильтр через {@code with*}, поэтому
 * текущую выборку легко сбросить к {@link #empty()}.</p>
 *
 * @param text           слова свободного поиска: PNR, фамилия пассажира или номер рейса
 * @param status         один статус брони (FL-01)
 * @param inProgressOnly только брони «в работе»: CREATED, PAID, CHECKED_IN (FL-01)
 * @param fareClass      класс обслуживания (FL-02)
 * @param flight         числовой ID или номер рейса (FL-03)
 * @param from           код IATA аэропорта вылета
 * @param to             код IATA аэропорта назначения
 * @param passenger      часть фамилии пассажира
 * @param departureFrom  дата вылета «с», включительно (FL-04)
 * @param departureTo    дата вылета «по», включительно (FL-04)
 * @param prices         ограничения стоимости (FL-05)
 */
public record BookingFilter(
        List<String> text,
        BookingStatus status,
        boolean inProgressOnly,
        FareClass fareClass,
        String flight,
        String from,
        String to,
        String passenger,
        LocalDate departureFrom,
        LocalDate departureTo,
        List<PriceConstraint> prices) {

    public enum Operator { LT, LTE, EQ, GTE, GT }
    public record PriceConstraint(Operator operator, BigDecimal value) { }

    public BookingFilter {
        text = text == null ? List.of() : List.copyOf(text);
        prices = prices == null ? List.of() : List.copyOf(prices);
        if (departureFrom != null && departureTo != null && departureFrom.isAfter(departureTo)) {
            throw new ValidationException(ErrorCode.E_102, "Дата «с» не может быть позже даты «по»");
        }
        validatePrices(prices);
    }

    /** Возвращает фильтр без ограничений. */
    public static BookingFilter empty() {
        return new BookingFilter(List.of(), null, false, null, null, null, null, null, null, null, List.of());
    }

    /** Проверяет, что ни одно ограничение не задано. */
    public boolean isEmpty() {
        return equals(empty());
    }

    /** Отбирает брони в одном статусе; снимает отбор «только активные». */
    public BookingFilter withStatus(BookingStatus value) {
        return new BookingFilter(text, value, false, fareClass, flight, from, to, passenger,
                departureFrom, departureTo, prices);
    }

    /** Отбирает брони «в работе»; снимает отбор по одному статусу. */
    public BookingFilter withInProgressOnly() {
        return new BookingFilter(text, null, true, fareClass, flight, from, to, passenger,
                departureFrom, departureTo, prices);
    }

    public BookingFilter withFareClass(FareClass value) {
        return new BookingFilter(text, status, inProgressOnly, value, flight, from, to, passenger,
                departureFrom, departureTo, prices);
    }

    public BookingFilter withFlight(String value) {
        return new BookingFilter(text, status, inProgressOnly, fareClass, value, from, to, passenger,
                departureFrom, departureTo, prices);
    }

    public BookingFilter withDepartureRange(LocalDate fromDate, LocalDate toDate) {
        return new BookingFilter(text, status, inProgressOnly, fareClass, flight, from, to, passenger,
                fromDate, toDate, prices);
    }

    /** Заменяет ограничения стоимости диапазоном; {@code null} — граница не задана. */
    public BookingFilter withPriceRange(BigDecimal min, BigDecimal max) {
        List<PriceConstraint> range = new ArrayList<>();
        if (min != null) range.add(new PriceConstraint(Operator.GTE, min));
        if (max != null) range.add(new PriceConstraint(Operator.LTE, max));
        return new BookingFilter(text, status, inProgressOnly, fareClass, flight, from, to, passenger,
                departureFrom, departureTo, range);
    }

    private static void validatePrices(List<PriceConstraint> prices) {
        BigDecimal lower = null;
        BigDecimal upper = null;
        for (PriceConstraint constraint : prices) {
            switch (constraint.operator()) {
                case GT, GTE -> lower = lower == null ? constraint.value() : lower.max(constraint.value());
                case LT, LTE -> upper = upper == null ? constraint.value() : upper.min(constraint.value());
                case EQ -> {
                    lower = lower == null ? constraint.value() : lower.max(constraint.value());
                    upper = upper == null ? constraint.value() : upper.min(constraint.value());
                }
            }
        }
        if (lower != null && upper != null && lower.compareTo(upper) > 0) {
            throw new ValidationException(ErrorCode.E_103, "Минимальная сумма не может быть больше максимальной");
        }
    }
}
