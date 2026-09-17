package ru.mirea.avia.util;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет форматирование и разбор денежных сумм (BR-06, NFR-16). */
class MoneyFormatTest {
    @Test
    void formatsWithThousandsSeparator() {
        assertThat(MoneyFormat.format(new BigDecimal("102390"))).isEqualTo("102 390,00");
        assertThat(MoneyFormat.format(new BigDecimal("6400.5"))).isEqualTo("6 400,50");
        assertThat(MoneyFormat.format(new BigDecimal("0.005"))).isEqualTo("0,01");
        assertThat(MoneyFormat.formatRub(new BigDecimal("12740.00"))).isEqualTo("12 740,00 руб.");
    }

    @Test
    void formatsPlainAmountAndPercent() {
        assertThat(MoneyFormat.formatPlain(new BigDecimal("6400"))).isEqualTo("6400,00");
        assertThat(MoneyFormat.formatPlain(new BigDecimal("102390.456"))).isEqualTo("102390,46");
        assertThat(MoneyFormat.formatPercent(new BigDecimal("14.3"))).isEqualTo("14,3 %");
        assertThat(MoneyFormat.formatPercent(new BigDecimal("14.25"))).isEqualTo("14,3 %");
    }

    @Test
    void formatsMissingAmountAsEmptyText() {
        assertThat(MoneyFormat.format(null)).isEmpty();
        assertThat(MoneyFormat.formatPlain(null)).isEmpty();
        assertThat(MoneyFormat.formatPercent(null)).isEmpty();
    }

    @Test
    void roundsHalfUpToKopecks() {
        assertThat(MoneyFormat.round(new BigDecimal("2.345"))).isEqualTo(new BigDecimal("2.35"));
        assertThat(MoneyFormat.round(new BigDecimal("2.344"))).isEqualTo(new BigDecimal("2.34"));
        assertThat(MoneyFormat.round(new BigDecimal("6400"))).isEqualTo(new BigDecimal("6400.00"));
    }

    @Test
    void parsesOperatorInput() {
        assertThat(MoneyFormat.parse("6 400,5")).isEqualTo(new BigDecimal("6400.50"));
        assertThat(MoneyFormat.parse("6400")).isEqualTo(new BigDecimal("6400.00"));
        assertThat(MoneyFormat.parse("6400.50")).isEqualTo(new BigDecimal("6400.50"));
        assertThat(MoneyFormat.parse(" 6 400 ")).isEqualTo(new BigDecimal("6400.00"));
        assertThat(MoneyFormat.parse("0")).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void rejectsMalformedAmount() {
        assertThatThrownBy(() -> MoneyFormat.parse("abc"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Сумма должна быть числом, например 6400 или 6400,50")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_103);
        assertThatThrownBy(() -> MoneyFormat.parse("1.234")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MoneyFormat.parse("1e999999999")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MoneyFormat.parse("-5")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MoneyFormat.parse("1234567890123")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MoneyFormat.parse("")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MoneyFormat.parse(null)).isInstanceOf(ValidationException.class);
    }
}
