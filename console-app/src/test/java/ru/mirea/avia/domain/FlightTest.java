package ru.mirea.avia.domain;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.InvalidStatusTransitionException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет маршрут, раскладку салона, закрытие продаж (BR-01) и статусы рейса. */
class FlightTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 15, 0);
    private static final Duration SALE_CLOSE = Duration.ofMinutes(60);

    @Test
    void describesRouteAndDuration() {
        Flight flight = flight(NOW.plusDays(1), 180);

        assertThat(flight.getRoute()).isEqualTo("SVO -> LED");
        assertThat(flight.getDuration()).isEqualTo(Duration.ofMinutes(80));
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
    }

    @Test
    void countsRowsBySixSeats() {
        assertThat(flight(NOW, 180).getRowCount()).isEqualTo(30);
        assertThat(flight(NOW, 181).getRowCount()).isEqualTo(31);
        assertThat(flight(NOW, 128).getRowCount()).isEqualTo(22);
        assertThat(flight(NOW, 6).getRowCount()).isEqualTo(1);
        assertThat(flight(NOW, 1).getRowCount()).isEqualTo(1);
    }

    @Test
    void capsRowCountAtTwoDigits() {
        assertThat(flight(NOW, 588).getRowCount()).isEqualTo(98);
        assertThat(flight(NOW, 594).getRowCount()).isEqualTo(Flight.MAX_ROW);
        assertThat(flight(NOW, 600).getRowCount()).isEqualTo(99);
    }

    @Test
    void saleClosesSixtyMinutesBeforeDeparture() {
        assertThat(flight(NOW.plusMinutes(60), 180).isOpenForSale(NOW, SALE_CLOSE)).isTrue();
        assertThat(flight(NOW.plusMinutes(59), 180).isOpenForSale(NOW, SALE_CLOSE)).isFalse();
        assertThat(flight(NOW.plusDays(7), 180).isOpenForSale(NOW, SALE_CLOSE)).isTrue();
        assertThat(flight(NOW.minusMinutes(1), 180).isOpenForSale(NOW, SALE_CLOSE)).isFalse();
    }

    @Test
    void delayedFlightIsBookable() {
        Flight flight = flight(NOW.plusMinutes(60), 180);
        flight.setStatus(FlightStatus.DELAYED);

        assertThat(flight.isOpenForSale(NOW, SALE_CLOSE)).isTrue();
    }

    @Test
    void cancelledFlightIsNotBookable() {
        Flight cancelled = flight(NOW.plusDays(7), 180);
        cancelled.setStatus(FlightStatus.CANCELLED);
        Flight departed = flight(NOW.plusDays(7), 180);
        departed.setStatus(FlightStatus.DEPARTED);
        Flight arrived = flight(NOW.plusDays(7), 180);
        arrived.setStatus(FlightStatus.ARRIVED);

        assertThat(cancelled.isOpenForSale(NOW, SALE_CLOSE)).isFalse();
        assertThat(departed.isOpenForSale(NOW, SALE_CLOSE)).isFalse();
        assertThat(arrived.isOpenForSale(NOW, SALE_CLOSE)).isFalse();
    }

    @Test
    void hasDepartedOnceDepartureTimeComes() {
        assertThat(flight(NOW.plusMinutes(1), 180).hasDeparted(NOW)).isFalse();
        assertThat(flight(NOW, 180).hasDeparted(NOW)).isTrue();
        assertThat(flight(NOW.minusDays(1), 180).hasDeparted(NOW)).isTrue();
    }

    @Test
    void hasDepartedByStatusBeforeScheduledTime() {
        Flight departed = flight(NOW.plusDays(1), 180);
        departed.setStatus(FlightStatus.DEPARTED);
        Flight arrived = flight(NOW.plusDays(1), 180);
        arrived.setStatus(FlightStatus.ARRIVED);
        Flight cancelled = flight(NOW.plusDays(1), 180);
        cancelled.setStatus(FlightStatus.CANCELLED);

        assertThat(departed.hasDeparted(NOW)).isTrue();
        assertThat(arrived.hasDeparted(NOW)).isTrue();
        assertThat(cancelled.hasDeparted(NOW)).isFalse();
    }

    @Test
    void permitsDelayAndReturnToSchedule() {
        Flight flight = flight(NOW.plusDays(1), 180);

        flight.changeStatus(FlightStatus.DELAYED);
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.DELAYED);
        flight.changeStatus(FlightStatus.SCHEDULED);
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
        flight.changeStatus(FlightStatus.DEPARTED);
        assertThatCode(() -> flight.changeStatus(FlightStatus.ARRIVED)).doesNotThrowAnyException();
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.ARRIVED);
    }

    @Test
    void rejectsSkippingDeparture() {
        Flight flight = flight(NOW.plusDays(1), 180);

        assertThatThrownBy(() -> flight.changeStatus(FlightStatus.ARRIVED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Переход SCHEDULED -> ARRIVED недопустим")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_302);
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
    }

    @Test
    void rejectsReturnFromDeparted() {
        Flight flight = flight(NOW.plusDays(1), 180);
        flight.setStatus(FlightStatus.DEPARTED);

        assertThatThrownBy(() -> flight.changeStatus(FlightStatus.SCHEDULED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Переход DEPARTED -> SCHEDULED недопустим");
    }

    @Test
    void cancelledFlightStatusIsFinal() {
        Flight flight = flight(NOW.plusDays(1), 180);
        flight.changeStatus(FlightStatus.CANCELLED);

        assertThatThrownBy(() -> flight.changeStatus(FlightStatus.SCHEDULED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Переход CANCELLED -> SCHEDULED недопустим. "
                        + "Рейс находится в финальном состоянии и не может быть изменён");
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.CANCELLED);
    }

    private static Flight flight(LocalDateTime departure, int seats) {
        return new Flight("SU1420", "Аэрофлот", "SVO", "LED", departure, departure.plusMinutes(80),
                "Airbus A320", seats, new BigDecimal("6400.00"));
    }
}
