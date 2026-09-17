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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет русские и английские команды умного фильтра бронирований. */
class SmartFilterParserTest {
    private final SmartFilterParser parser = new SmartFilterParser();

    @Test
    void parsesMixedQuery() {
        BookingFilter filter = parser.parse(
                "Иванов статус:Оплачено класс:бизнес рейс:su1420 откуда:SVO куда:led пассажир:Петр цена>=5000 AB12");

        assertThat(filter.text()).containsExactly("Иванов", "AB12");
        assertThat(filter.status()).isEqualTo(BookingStatus.PAID);
        assertThat(filter.inProgressOnly()).isFalse();
        assertThat(filter.fareClass()).isEqualTo(FareClass.BUSINESS);
        assertThat(filter.flight()).isEqualTo("su1420");
        assertThat(filter.from()).isEqualTo("SVO");
        assertThat(filter.to()).isEqualTo("led");
        assertThat(filter.passenger()).isEqualTo("Петр");
        assertThat(filter.departureFrom()).isNull();
        assertThat(filter.prices()).containsExactly(new PriceConstraint(Operator.GTE, new BigDecimal("5000.00")));
    }

    @Test
    void parsesEnglishKeys() {
        BookingFilter filter = parser.parse(
                "STATUS:checked-in class:Economy flight:3 from:svo to:LED passenger:sid");

        assertThat(filter.text()).isEmpty();
        assertThat(filter.status()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(filter.fareClass()).isEqualTo(FareClass.ECONOMY);
        assertThat(filter.flight()).isEqualTo("3");
        assertThat(filter.from()).isEqualTo("svo");
        assertThat(filter.to()).isEqualTo("LED");
        assertThat(filter.passenger()).isEqualTo("sid");
    }

    @Test
    void parsesStatusCodesAndShortTitles() {
        assertThat(parser.parse("статус:создано").status()).isEqualTo(BookingStatus.CREATED);
        assertThat(parser.parse("статус:регистрация").status()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(parser.parse("статус:выполнено").status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(parser.parse("status:canceled").status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(parser.parse("status:checked_in").status()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(parser.parse("status:EXPIRED").status()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(parser.parse("класс:комфорт").fareClass()).isEqualTo(FareClass.COMFORT);
    }

    @Test
    void parsesInProgressFlag() {
        assertThat(parser.parse("активные:да").inProgressOnly()).isTrue();
        assertThat(parser.parse("active:YES").inProgressOnly()).isTrue();
        assertThat(parser.parse("active:true").inProgressOnly()).isTrue();
        assertThat(parser.parse("активные:нет").inProgressOnly()).isFalse();
        assertThat(parser.parse("active:false").isEmpty()).isTrue();
    }

    @Test
    void supportsQuotedFreeText() {
        BookingFilter filter = parser.parse("\"Иванов Иван\" пассажир:\"Сидоров\" статус:cancelled");

        assertThat(filter.text()).containsExactly("Иванов Иван");
        assertThat(filter.passenger()).isEqualTo("Сидоров");
        assertThat(filter.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void keepsUnknownKeysAsFreeText() {
        BookingFilter filter = parser.parse("город:Москва SU1420");

        assertThat(filter.text()).containsExactly("город:Москва", "SU1420");
        assertThat(filter.flight()).isNull();
    }

    @Test
    void treatsBlankQueryAsEmptyFilter() {
        assertThat(parser.parse(null).isEmpty()).isTrue();
        assertThat(parser.parse("   ").isEmpty()).isTrue();
        assertThat(parser.parse("рейс: откуда:").isEmpty()).isTrue();
    }

    @Test
    void rejectsTooLongQuery() {
        assertThatThrownBy(() -> parser.parse("а".repeat(501)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Строка фильтра слишком длинная")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_103);
        assertThatCode(() -> parser.parse("а".repeat(500))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> parser.parse("статус:забронировано"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Неизвестный статус: забронировано")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_103);
    }

    @Test
    void rejectsUnknownFareClassAndFlag() {
        assertThatThrownBy(() -> parser.parse("класс:первый"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Неизвестный класс обслуживания: первый");
        assertThatThrownBy(() -> parser.parse("активные:иногда"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Поле «активные» принимает да/нет");
    }

    @Test
    void parsesPriceOperators() {
        BookingFilter filter = parser.parse("цена>5000 ЦЕНА<=15000,5 price<20000 price>=100.25 цена=6400");

        assertThat(filter.text()).isEmpty();
        assertThat(filter.prices()).containsExactly(
                new PriceConstraint(Operator.GT, new BigDecimal("5000.00")),
                new PriceConstraint(Operator.LTE, new BigDecimal("15000.50")),
                new PriceConstraint(Operator.LT, new BigDecimal("20000.00")),
                new PriceConstraint(Operator.GTE, new BigDecimal("100.25")),
                new PriceConstraint(Operator.EQ, new BigDecimal("6400.00")));
    }

    @Test
    void keepsMalformedPriceAsFreeText() {
        assertThat(parser.parse("цена>1.234").text()).containsExactly("цена>1.234");
        assertThat(parser.parse("цена>abc").prices()).isEmpty();
    }

    @Test
    void rejectsContradictoryPrices() {
        assertThatThrownBy(() -> parser.parse("цена>10000 цена<5000"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Минимальная сумма не может быть больше максимальной");
    }

    @Test
    void parsesDates() {
        BookingFilter russian = parser.parse("с:16.09.2026 по:30.09.2026");
        BookingFilter english = parser.parse("since:01.10.2026 until:01.10.2026");

        assertThat(russian.departureFrom()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(russian.departureTo()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(english.departureFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(english.departureTo()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void rejectsMalformedOrReversedDates() {
        assertThatThrownBy(() -> parser.parse("с:2026-09-16"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Дата должна быть в формате дд.ММ.гггг")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_102);
        assertThatThrownBy(() -> parser.parse("по:31.02.2026"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_102);
        assertThatThrownBy(() -> parser.parse("с:30.09.2026 по:16.09.2026"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Дата «с» не может быть позже даты «по»");
    }
}
