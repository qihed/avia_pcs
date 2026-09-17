package ru.mirea.avia.dto;

import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.dto.FlightDtos.FlightResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** DTO-контракты раздела «Статистика» (ST-01…ST-11). */
public final class AnalyticsDtos {
    private AnalyticsDtos() {
    }

    /** Все показатели раздела за один вызов; форматирование выполняет слой представления. */
    public record StatisticsResponse(
            long passengerCount,
            long flightCount,
            long flightsOpenForSale,
            long bookingCount,
            Map<BookingStatus, Long> bookingsByStatus,
            Map<FareClass, Long> bookingsByFareClass,
            BigDecimal totalRevenue,
            BigDecimal averagePaidPrice,
            BigDecimal averageLoadPercent,
            List<FlightCount> topFlights,
            List<NamedCount> topRoutes,
            BigDecimal cancelledSharePercent) {

        /** Возвращает долю класса обслуживания среди всех броней, % (ST-05). */
        public BigDecimal fareClassSharePercent(FareClass fareClass) {
            return percent(bookingsByFareClass.getOrDefault(fareClass, 0L), bookingCount);
        }

        /** Возвращает долю {@code part} от {@code total} в процентах с одним знаком после запятой. */
        public static BigDecimal percent(long part, long total) {
            if (total == 0) return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
            return BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        }
    }

    /** Рейс и число броней на нём (ST-09). */
    public record FlightCount(FlightResponse flight, long bookings) {
    }

    /** Подпись и счётчик: направление «откуда — куда» и число броней (ST-10). */
    public record NamedCount(String name, long count) {
    }
}
