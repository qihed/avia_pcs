package ru.mirea.avia.service;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.dto.BookingDtos.PriceQuote;
import ru.mirea.avia.error.ValidationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет расчёт стоимости билета по правилу BR-06. */
class FareCalculatorTest {
    private final FareCalculator calculator = new FareCalculator();

    @Test
    void businessClassIncludesBaggageWithoutFee() {
        PriceQuote quote = calculator.calculate(new BigDecimal("8300.00"), FareClass.BUSINESS, true);
        assertThat(quote.total()).isEqualTo(new BigDecimal("20750.00"));
        assertThat(quote.baggageFee()).isEqualTo(new BigDecimal("0.00"));
        assertThat(quote.coefficient()).isEqualTo(new BigDecimal("2.5"));
        assertThat(quote.fareClass()).isEqualTo(FareClass.BUSINESS);
        assertThat(quote.basePrice()).isEqualTo(new BigDecimal("8300.00"));
    }

    @Test
    void comfortClassAddsBaggageFee() {
        PriceQuote quote = calculator.calculate(new BigDecimal("6400.00"), FareClass.COMFORT, true);
        assertThat(quote.total()).isEqualTo(new BigDecimal("12740.00"));
        assertThat(quote.baggageFee()).isEqualTo(FareCalculator.BAGGAGE_FEE);
        assertThat(quote.coefficient()).isEqualTo(new BigDecimal("1.6"));
    }

    @Test
    void calculatesEconomyAndComfortPricesOfDemoBookings() {
        assertThat(total("6400", FareClass.ECONOMY, false)).isEqualTo(new BigDecimal("6400.00"));
        assertThat(total("6400", FareClass.ECONOMY, true)).isEqualTo(new BigDecimal("8900.00"));
        assertThat(total("8300", FareClass.ECONOMY, false)).isEqualTo(new BigDecimal("8300.00"));
        assertThat(total("5200", FareClass.ECONOMY, false)).isEqualTo(new BigDecimal("5200.00"));
        assertThat(total("4900", FareClass.ECONOMY, false)).isEqualTo(new BigDecimal("4900.00"));
        assertThat(total("4900", FareClass.ECONOMY, true)).isEqualTo(new BigDecimal("7400.00"));
        assertThat(total("8300", FareClass.COMFORT, true)).isEqualTo(new BigDecimal("15780.00"));
        assertThat(total("6400", FareClass.COMFORT, true)).isEqualTo(new BigDecimal("12740.00"));
        assertThat(total("5200", FareClass.COMFORT, false)).isEqualTo(new BigDecimal("8320.00"));
        assertThat(total("8300", FareClass.COMFORT, false)).isEqualTo(new BigDecimal("13280.00"));
    }

    @Test
    void calculatesBusinessPricesOfDemoBookings() {
        assertThat(total("6400", FareClass.BUSINESS, true)).isEqualTo(new BigDecimal("16000.00"));
        assertThat(total("8300", FareClass.BUSINESS, true)).isEqualTo(new BigDecimal("20750.00"));
        assertThat(total("8300", FareClass.BUSINESS, false)).isEqualTo(new BigDecimal("20750.00"));
    }

    @Test
    void roundsHalfUpToKopecks() {
        assertThat(total("1234.565", FareClass.ECONOMY, false)).isEqualTo(new BigDecimal("1234.57"));
        assertThat(total("1234.564", FareClass.ECONOMY, false)).isEqualTo(new BigDecimal("1234.56"));
        assertThat(total("0.03", FareClass.COMFORT, false)).isEqualTo(new BigDecimal("0.05"));
        assertThat(total("0.01", FareClass.BUSINESS, false)).isEqualTo(new BigDecimal("0.03"));
    }

    @Test
    void rejectsNonPositiveBasePrice() {
        assertThatThrownBy(() -> calculator.calculate(BigDecimal.ZERO, FareClass.ECONOMY, false))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Базовый тариф рейса должен быть больше нуля");
        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("-100"), FareClass.BUSINESS, true))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103");
    }

    @Test
    void rejectsPriceRoundedToZero() {
        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("0.001"), FareClass.ECONOMY, false))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Стоимость билета должна быть больше нуля");
    }

    private BigDecimal total(String basePrice, FareClass fareClass, boolean baggageIncluded) {
        return calculator.calculate(new BigDecimal(basePrice), fareClass, baggageIncluded).total();
    }
}
