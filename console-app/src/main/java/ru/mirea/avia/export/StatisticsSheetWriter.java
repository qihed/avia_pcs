package ru.mirea.avia.export;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.dto.AnalyticsDtos.FlightCount;
import ru.mirea.avia.dto.AnalyticsDtos.NamedCount;
import ru.mirea.avia.dto.AnalyticsDtos.StatisticsResponse;
import ru.mirea.avia.dto.FlightDtos.FlightResponse;
import ru.mirea.avia.util.DateTimeFormat;

import java.math.BigDecimal;
import java.util.List;

/**
 * Лист «Статистика» книги Excel.
 *
 * <p>Показатели ST-01…ST-11 записываются парами «показатель — значение», а распределения броней
 * по статусам и классам обслуживания дополняются долей в процентах. Номер следующей строки
 * вычисляется по последней заполненной, поэтому писатель не хранит изменяемого состояния.</p>
 */
final class StatisticsSheetWriter {
    private static final List<String> HEADERS = List.of("Показатель", "Значение", "Доля, %");
    private static final String INDENT = "  ";

    private final Sheet sheet;
    private final ExcelStyles styles;

    StatisticsSheetWriter(Sheet sheet, ExcelStyles styles) {
        this.sheet = sheet;
        this.styles = styles;
    }

    /** Заполняет лист показателями раздела «Статистика». */
    void write(StatisticsResponse statistics) {
        styles.header(sheet, HEADERS);
        writeTotals(statistics);
        writeStatuses(statistics);
        writeFareClasses(statistics);
        writeTopFlights(statistics.topFlights());
        writeTopRoutes(statistics.topRoutes());
        sheet.createFreezePane(0, 1);
        styles.autoSize(sheet, HEADERS.size());
    }

    private void writeTotals(StatisticsResponse statistics) {
        count("ST-01 Всего пассажиров", statistics.passengerCount());
        count("ST-02 Всего рейсов", statistics.flightCount());
        count("ST-02 Рейсов, доступных для продажи", statistics.flightsOpenForSale());
        count("ST-03 Всего бронирований", statistics.bookingCount());
        money("ST-06 Суммарная выручка, руб.", statistics.totalRevenue());
        money("ST-07 Средняя стоимость билета, руб.", statistics.averagePaidPrice());
        percent("ST-08 Средняя загрузка рейсов", statistics.averageLoadPercent());
        percent("ST-11 Доля отменённых и просроченных броней", statistics.cancelledSharePercent());
    }

    private void writeStatuses(StatisticsResponse statistics) {
        section("ST-04 Распределение броней по статусам");
        for (BookingStatus status : BookingStatus.values()) {
            long count = statistics.bookingsByStatus().getOrDefault(status, 0L);
            BigDecimal share = StatisticsResponse.percent(count, statistics.bookingCount());
            countWithShare(INDENT + status.getTitle(), count, share);
        }
    }

    private void writeFareClasses(StatisticsResponse statistics) {
        section("ST-05 Распределение броней по классам обслуживания");
        for (FareClass fareClass : FareClass.values()) {
            long count = statistics.bookingsByFareClass().getOrDefault(fareClass, 0L);
            countWithShare(INDENT + fareClass.getTitle(), count, statistics.fareClassSharePercent(fareClass));
        }
    }

    private void writeTopFlights(List<FlightCount> topFlights) {
        section("ST-09 Топ-5 рейсов по числу броней");
        for (int i = 0; i < topFlights.size(); i++) {
            FlightResponse flight = topFlights.get(i).flight();
            String label = INDENT + (i + 1) + ". " + flight.flightNumber() + " " + flight.route() + " "
                    + DateTimeFormat.format(flight.departureTime().toLocalDate());
            count(label, topFlights.get(i).bookings());
        }
    }

    private void writeTopRoutes(List<NamedCount> topRoutes) {
        section("ST-10 Топ-3 направления");
        for (int i = 0; i < topRoutes.size(); i++) {
            count(INDENT + (i + 1) + ". " + topRoutes.get(i).name(), topRoutes.get(i).count());
        }
    }

    private void section(String title) {
        // Пропущенная строка отделяет новый раздел от предыдущих показателей.
        Row row = sheet.createRow(sheet.getLastRowNum() + 2);
        styles.bold(row, 0, title);
    }

    private Row nextRow(String label) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        styles.text(row, 0, label);
        return row;
    }

    private void count(String label, long value) {
        styles.number(nextRow(label), 1, value);
    }

    private void countWithShare(String label, long value, BigDecimal share) {
        Row row = nextRow(label);
        styles.number(row, 1, value);
        styles.percent(row, 2, share);
    }

    private void money(String label, BigDecimal value) {
        styles.money(nextRow(label), 1, value);
    }

    private void percent(String label, BigDecimal value) {
        styles.percent(nextRow(label), 1, value);
    }
}
