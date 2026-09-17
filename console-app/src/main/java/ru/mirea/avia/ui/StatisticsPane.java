package ru.mirea.avia.ui;

import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.dto.AnalyticsDtos.FlightCount;
import ru.mirea.avia.dto.AnalyticsDtos.NamedCount;
import ru.mirea.avia.dto.AnalyticsDtos.StatisticsResponse;
import ru.mirea.avia.dto.FlightDtos.FlightResponse;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.StatisticsService;
import ru.mirea.avia.util.DateTimeFormat;
import ru.mirea.avia.util.MoneyFormat;
import ru.mirea.avia.util.UiText;

import java.util.List;

/**
 * Раздел «Статистика»: одиннадцать показателей ST-01…ST-11 (FR-15, UC-10, форма п. 6.8 ТЗ).
 *
 * <p>Отчёт печатается один раз за вызов. Подписи выравниваются точками до общей ширины,
 * поэтому значения образуют ровный столбец.</p>
 */
public final class StatisticsPane {
    private static final int LEADER_WIDTH = 52;
    private static final int MIN_DOTS = 3;

    private final StatisticsService statistics;
    private final BookingService bookings;
    private final ConsoleIo io;

    public StatisticsPane(StatisticsService statistics, BookingService bookings, ConsoleIo io) {
        this.statistics = statistics;
        this.bookings = bookings;
        this.io = io;
    }

    /** Аннулирует просроченные брони (BR-09) и печатает отчёт со всеми показателями. */
    public void show() {
        // Статусы должны быть актуальными, иначе просроченные брони попадут в число созданных.
        bookings.expireOutdated();
        StatisticsResponse report = statistics.calculate();
        io.println();
        io.println(ConsoleIo.titledLine("СТАТИСТИКА СИСТЕМЫ", '='));
        row("Всего пассажиров", String.valueOf(report.passengerCount()));
        row("Всего рейсов", report.flightCount() + "   (доступно к продаже: " + report.flightsOpenForSale() + ")");
        row("Всего бронирований", String.valueOf(report.bookingCount()));
        printDistributions(report);

        io.println();
        row("Суммарная выручка", MoneyFormat.formatRub(report.totalRevenue()));
        row("Средняя стоимость билета", MoneyFormat.formatRub(report.averagePaidPrice()));
        row("Средняя загрузка рейсов", MoneyFormat.formatPercent(report.averageLoadPercent()));
        row("Доля отменённых и просроченных", MoneyFormat.formatPercent(report.cancelledSharePercent()));
        printTopFlights(report.topFlights());
        printTopRoutes(report.topRoutes());
        io.println(ConsoleIo.line('='));
    }

    /** ST-04, ST-05. */
    private void printDistributions(StatisticsResponse report) {
        io.println();
        Ui.info(io, "По статусам:");
        for (BookingStatus status : BookingStatus.values()) {
            subRow(UiText.status(status), String.valueOf(report.bookingsByStatus().getOrDefault(status, 0L)));
        }

        io.println();
        Ui.info(io, "По классам обслуживания:");
        for (FareClass fareClass : FareClass.values()) {
            long count = report.bookingsByFareClass().getOrDefault(fareClass, 0L);
            String share = MoneyFormat.formatPercent(report.fareClassSharePercent(fareClass));
            subRow(UiText.fareClass(fareClass), "%-5d (%s)".formatted(count, share));
        }
    }

    /** ST-09. */
    private void printTopFlights(List<FlightCount> topFlights) {
        io.println();
        Ui.info(io, "Топ-5 рейсов по числу броней:");
        for (int i = 0; i < topFlights.size(); i++) {
            FlightResponse flight = topFlights.get(i).flight();
            String label = (i + 1) + ". " + flight.flightNumber() + " " + flight.route() + " "
                    + DateTimeFormat.format(flight.departureTime().toLocalDate());
            subRow(label, String.valueOf(topFlights.get(i).bookings()));
        }
    }

    /** ST-10. */
    private void printTopRoutes(List<NamedCount> topRoutes) {
        io.println();
        Ui.info(io, "Топ-3 направления:");
        for (int i = 0; i < topRoutes.size(); i++) {
            NamedCount route = topRoutes.get(i);
            subRow((i + 1) + ". " + route.name(), UiText.bookings(route.count()));
        }
    }

    private void row(String label, String value) {
        io.println("   " + leader(label, LEADER_WIDTH) + " " + value);
    }

    private void subRow(String label, String value) {
        io.println("     " + leader(label, LEADER_WIDTH - 2) + " " + value);
    }

    /** Возвращает подпись с точками до заданной ширины: «Всего пассажиров .......». */
    private static String leader(String label, int width) {
        int dots = Math.max(MIN_DOTS, width - label.length() - 1);
        return label + " " + ".".repeat(dots);
    }
}
