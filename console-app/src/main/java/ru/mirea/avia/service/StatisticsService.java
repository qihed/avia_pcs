package ru.mirea.avia.service;

import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.dto.AnalyticsDtos.FlightCount;
import ru.mirea.avia.dto.AnalyticsDtos.NamedCount;
import ru.mirea.avia.dto.AnalyticsDtos.StatisticsResponse;
import ru.mirea.avia.jdbc.Transactions;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.FlightRepository;
import ru.mirea.avia.repository.PassengerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Рассчитывает одиннадцать показателей раздела «Статистика» (FR-15, ST-01…ST-11).
 *
 * <p>Рейсы и брони читаются из СУБД одной транзакцией, а показатели вычисляются
 * средствами Stream API.</p>
 */
public class StatisticsService {
    private static final int TOP_FLIGHTS = 5;
    private static final int TOP_ROUTES = 3;

    private final PassengerRepository passengers;
    private final FlightRepository flights;
    private final BookingRepository bookings;
    private final FlightMapper mapper;
    private final Transactions transactions;

    public StatisticsService(PassengerRepository passengers, FlightRepository flights,
                             BookingRepository bookings, FlightMapper mapper, Transactions transactions) {
        this.passengers = passengers;
        this.flights = flights;
        this.bookings = bookings;
        this.mapper = mapper;
        this.transactions = transactions;
    }

    /** Рассчитывает все показатели раздела за один вызов. */
    public StatisticsResponse calculate() {
        return transactions.read(() -> summarize(passengers.count(), flights.findAll(), bookings.findAll()));
    }

    private StatisticsResponse summarize(long passengerCount, List<Flight> schedule, List<Booking> all) {
        long total = all.size();
        long cancelledOrExpired = all.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CANCELLED
                        || booking.getStatus() == BookingStatus.EXPIRED)
                .count();
        return new StatisticsResponse(
                passengerCount,
                schedule.size(),
                schedule.stream().filter(flight -> flight.getStatus().isOpenForSale()).count(),
                total,
                countByEnum(all, Booking::getStatus, BookingStatus.class),
                countByEnum(all, Booking::getFareClass, FareClass.class),
                totalRevenue(all),
                averagePaidPrice(all),
                averageLoad(schedule, all),
                topFlights(all),
                topRoutes(all),
                StatisticsResponse.percent(cancelledOrExpired, total));
    }

    /** ST-09: группировка по рейсу, по убыванию числа броней; при равенстве — по времени вылета. */
    private List<FlightCount> topFlights(List<Booking> all) {
        Map<Flight, Long> byFlight = all.stream()
                .collect(Collectors.groupingBy(Booking::getFlight, Collectors.counting()));
        return byFlight.entrySet().stream()
                .map(entry -> new FlightCount(mapper.toResponse(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingLong(FlightCount::bookings).reversed()
                        .thenComparing(count -> count.flight().departureTime())
                        .thenComparingLong(count -> count.flight().id()))
                .limit(TOP_FLIGHTS)
                .toList();
    }

    /** ST-04, ST-05: количество по каждому значению перечисления, включая нулевые. */
    private static <E extends Enum<E>> Map<E, Long> countByEnum(List<Booking> all, Function<Booking, E> key,
                                                                Class<E> type) {
        Map<E, Long> result = new EnumMap<>(type);
        Arrays.stream(type.getEnumConstants()).forEach(value -> result.put(value, 0L));
        result.putAll(all.stream().collect(Collectors.groupingBy(key, Collectors.counting())));
        return result;
    }

    /** ST-06: сумма стоимости броней в статусах PAID, CHECKED_IN и COMPLETED. */
    private static BigDecimal totalRevenue(List<Booking> all) {
        return all.stream()
                .filter(booking -> booking.getStatus().isPaid())
                .map(Booking::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** ST-07: средняя стоимость оплаченного билета. */
    private static BigDecimal averagePaidPrice(List<Booking> all) {
        List<BigDecimal> paid = all.stream()
                .filter(booking -> booking.getStatus().isPaid())
                .map(Booking::getPrice)
                .toList();
        if (paid.isEmpty()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return paid.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(paid.size()), 2, RoundingMode.HALF_UP);
    }

    /** ST-08: активные брони, делённые на суммарную вместимость всех рейсов, в процентах. */
    private static BigDecimal averageLoad(List<Flight> schedule, List<Booking> all) {
        long capacity = schedule.stream().mapToLong(Flight::getTotalSeats).sum();
        long active = all.stream().filter(Booking::isActive).count();
        return StatisticsResponse.percent(active, capacity);
    }

    /** ST-10: группировка по паре аэропортов; при равенстве — по самому раннему вылету. */
    private static List<NamedCount> topRoutes(List<Booking> all) {
        Map<String, List<Booking>> byRoute = all.stream()
                .collect(Collectors.groupingBy(booking -> booking.getFlight().getRoute()));
        Map<String, LocalDateTime> firstDeparture = byRoute.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                        .map(booking -> booking.getFlight().getDepartureTime())
                        .min(Comparator.naturalOrder())
                        .orElseThrow()));
        return byRoute.entrySet().stream()
                .map(entry -> new NamedCount(entry.getKey(), entry.getValue().size()))
                .sorted(Comparator.comparingLong(NamedCount::count).reversed()
                        .thenComparing(route -> firstDeparture.get(route.name())))
                .limit(TOP_ROUTES)
                .toList();
    }
}
