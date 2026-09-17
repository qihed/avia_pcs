package ru.mirea.avia.service;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.dto.AnalyticsDtos.FlightCount;
import ru.mirea.avia.dto.AnalyticsDtos.NamedCount;
import ru.mirea.avia.dto.AnalyticsDtos.StatisticsResponse;
import ru.mirea.avia.support.TestContext;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Фиксирует одиннадцать показателей раздела «Статистика» на демонстрационных данных (TC-16). */
class StatisticsServiceTest {
    private final StatisticsResponse statistics = new TestContext().withDemoData().statisticsService.calculate();

    @Test
    void countsPassengersFlightsAndBookings() {
        assertThat(statistics.passengerCount()).isEqualTo(6);
        assertThat(statistics.flightCount()).isEqualTo(8);
        assertThat(statistics.flightsOpenForSale()).isEqualTo(5);
        assertThat(statistics.bookingCount()).isEqualTo(14);
    }

    @Test
    void distributesBookingsByStatus() {
        assertThat(statistics.bookingsByStatus()).isEqualTo(Map.of(
                BookingStatus.CREATED, 3L,
                BookingStatus.PAID, 5L,
                BookingStatus.CHECKED_IN, 2L,
                BookingStatus.COMPLETED, 2L,
                BookingStatus.CANCELLED, 1L,
                BookingStatus.EXPIRED, 1L));
    }

    @Test
    void distributesBookingsByFareClassWithShares() {
        assertThat(statistics.bookingsByFareClass()).isEqualTo(Map.of(
                FareClass.ECONOMY, 8L,
                FareClass.COMFORT, 4L,
                FareClass.BUSINESS, 2L));
        assertThat(statistics.fareClassSharePercent(FareClass.ECONOMY)).isEqualTo(new BigDecimal("57.1"));
        assertThat(statistics.fareClassSharePercent(FareClass.COMFORT)).isEqualTo(new BigDecimal("28.6"));
        assertThat(statistics.fareClassSharePercent(FareClass.BUSINESS)).isEqualTo(new BigDecimal("14.3"));
    }

    @Test
    void calculatesRevenueAverageLoadAndCancelledShare() {
        assertThat(statistics.totalRevenue()).isEqualTo(new BigDecimal("102390.00"));
        assertThat(statistics.averagePaidPrice()).isEqualTo(new BigDecimal("11376.67"));
        assertThat(statistics.averageLoadPercent()).isEqualTo(new BigDecimal("0.9"));
        assertThat(statistics.cancelledSharePercent()).isEqualTo(new BigDecimal("14.3"));
    }

    @Test
    void ranksTopFlightsByBookingsThenDeparture() {
        assertThat(statistics.topFlights())
                .extracting(count -> count.flight().flightNumber())
                .containsExactly("SU1420", "SU1108", "FV6021", "U62810", "SU1416");
        assertThat(statistics.topFlights())
                .extracting(FlightCount::bookings)
                .containsExactly(3L, 3L, 3L, 2L, 1L);
    }

    @Test
    void ranksTopRoutesByBookingsThenEarliestDeparture() {
        assertThat(statistics.topRoutes())
                .extracting(NamedCount::name)
                .containsExactly("SVO -> LED", "SVO -> AER", "LED -> KZN");
        assertThat(statistics.topRoutes())
                .extracting(NamedCount::count)
                .containsExactly(4L, 4L, 3L);
    }

    @Test
    void emptyDatabaseGivesZeros() {
        StatisticsResponse empty = new TestContext().statisticsService.calculate();
        assertThat(empty.passengerCount()).isZero();
        assertThat(empty.flightCount()).isZero();
        assertThat(empty.flightsOpenForSale()).isZero();
        assertThat(empty.bookingCount()).isZero();
        assertThat(empty.bookingsByStatus()).hasSize(6).containsEntry(BookingStatus.PAID, 0L);
        assertThat(empty.bookingsByFareClass()).hasSize(3).containsEntry(FareClass.BUSINESS, 0L);
        assertThat(empty.fareClassSharePercent(FareClass.ECONOMY)).isEqualTo(new BigDecimal("0.0"));
        assertThat(empty.totalRevenue()).isEqualTo(new BigDecimal("0.00"));
        assertThat(empty.averagePaidPrice()).isEqualTo(new BigDecimal("0.00"));
        assertThat(empty.averageLoadPercent()).isEqualTo(new BigDecimal("0.0"));
        assertThat(empty.cancelledSharePercent()).isEqualTo(new BigDecimal("0.0"));
        assertThat(empty.topFlights()).isEmpty();
        assertThat(empty.topRoutes()).isEmpty();
    }
}
