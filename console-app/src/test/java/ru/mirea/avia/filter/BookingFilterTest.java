package ru.mirea.avia.filter;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;
import ru.mirea.avia.filter.BookingFilter.Operator;
import ru.mirea.avia.filter.BookingFilter.PriceConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет неизменяемые критерии отбора и проверку диапазонов дат и стоимости (FL-01…FL-05). */
class BookingFilterTest {
    private static final LocalDate SEP_16 = LocalDate.of(2026, 9, 16);
    private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);

    private final BookingFilter empty = BookingFilter.empty();

    @Test
    void emptyFilterHasNoCriteria() {
        BookingFilter blank = new BookingFilter(null, null, false, null, null, null, null, null, null, null, null);

        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.text()).isEmpty();
        assertThat(empty.prices()).isEmpty();
        assertThat(blank).isEqualTo(empty);
        assertThat(blank.isEmpty()).isTrue();
    }

    @Test
    void withersReturnNewFilterAndKeepOriginal() {
        BookingFilter paid = empty.withStatus(BookingStatus.PAID);

        assertThat(paid.status()).isEqualTo(BookingStatus.PAID);
        assertThat(paid.isEmpty()).isFalse();
        assertThat(empty.status()).isNull();
        assertThat(empty.isEmpty()).isTrue();
    }

    @Test
    void statusAndInProgressReplaceEachOther() {
        BookingFilter active = empty.withStatus(BookingStatus.PAID).withInProgressOnly();
        BookingFilter cancelled = active.withStatus(BookingStatus.CANCELLED);

        assertThat(active.status()).isNull();
        assertThat(active.inProgressOnly()).isTrue();
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.inProgressOnly()).isFalse();
    }

    @Test
    void withersReplaceOnlyTheirCriterion() {
        BookingFilter filter = empty.withStatus(BookingStatus.PAID)
                .withFareClass(FareClass.BUSINESS)
                .withFlight("SU1420")
                .withDepartureRange(SEP_16, SEP_30)
                .withPriceRange(new BigDecimal("1000"), new BigDecimal("20000"));

        BookingFilter changed = filter.withFareClass(FareClass.ECONOMY).withFlight("3");

        assertThat(changed.fareClass()).isEqualTo(FareClass.ECONOMY);
        assertThat(changed.flight()).isEqualTo("3");
        assertThat(changed.status()).isEqualTo(BookingStatus.PAID);
        assertThat(changed.departureFrom()).isEqualTo(SEP_16);
        assertThat(changed.departureTo()).isEqualTo(SEP_30);
        assertThat(changed.prices()).isEqualTo(filter.prices());
        assertThat(filter.fareClass()).isEqualTo(FareClass.BUSINESS);
        assertThat(changed.withFareClass(null).fareClass()).isNull();
        assertThat(changed.withFlight(null).flight()).isNull();
    }

    @Test
    void priceRangeReplacesPreviousConstraints() {
        BookingFilter range = empty.withPriceRange(new BigDecimal("1000"), new BigDecimal("5000"));
        BookingFilter upperOnly = range.withPriceRange(null, new BigDecimal("9000"));

        assertThat(range.prices()).containsExactly(
                new PriceConstraint(Operator.GTE, new BigDecimal("1000")),
                new PriceConstraint(Operator.LTE, new BigDecimal("5000")));
        assertThat(upperOnly.prices()).containsExactly(new PriceConstraint(Operator.LTE, new BigDecimal("9000")));
        assertThat(upperOnly.withPriceRange(null, null).isEmpty()).isTrue();
    }

    @Test
    void acceptsOpenAndSingleDayDateRanges() {
        assertThatCode(() -> empty.withDepartureRange(SEP_16, SEP_16)).doesNotThrowAnyException();
        assertThatCode(() -> empty.withDepartureRange(null, SEP_16)).doesNotThrowAnyException();
        assertThatCode(() -> empty.withDepartureRange(SEP_30, null)).doesNotThrowAnyException();
        assertThat(empty.withDepartureRange(SEP_16, SEP_30).withDepartureRange(null, null).isEmpty()).isTrue();
    }

    @Test
    void rejectsReversedDateRange() {
        assertThatThrownBy(() -> empty.withDepartureRange(SEP_30, SEP_16))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Дата «с» не может быть позже даты «по»")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_102);
    }

    @Test
    void acceptsEqualPriceBounds() {
        assertThatCode(() -> empty.withPriceRange(new BigDecimal("6400"), new BigDecimal("6400.00")))
                .doesNotThrowAnyException();
        assertThatCode(() -> empty.withPriceRange(new BigDecimal("15000"), null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMinimumPriceAboveMaximum() {
        assertThatThrownBy(() -> empty.withPriceRange(BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Минимальная сумма не может быть больше максимальной")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_103);
    }

    @Test
    void rejectsContradictoryPriceConstraints() {
        List<PriceConstraint> greaterThanExact = List.of(
                new PriceConstraint(Operator.GT, new BigDecimal("5000")),
                new PriceConstraint(Operator.EQ, new BigDecimal("4000")));
        List<PriceConstraint> twoExact = List.of(
                new PriceConstraint(Operator.EQ, new BigDecimal("5000")),
                new PriceConstraint(Operator.EQ, new BigDecimal("6000")));

        assertThatThrownBy(() -> withPrices(greaterThanExact)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> withPrices(twoExact)).isInstanceOf(ValidationException.class);
    }

    @Test
    void copiesCriteriaLists() {
        List<String> words = new ArrayList<>(List.of("SU1420"));
        BookingFilter filter = new BookingFilter(words, null, false, null, null, null, null, null, null, null, null);

        words.add("Иванов");

        assertThat(filter.text()).containsExactly("SU1420");
        assertThatThrownBy(() -> filter.text().add("Петрова")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static BookingFilter withPrices(List<PriceConstraint> prices) {
        return new BookingFilter(List.of(), null, false, null, null, null, null, null, null, null, prices);
    }
}
