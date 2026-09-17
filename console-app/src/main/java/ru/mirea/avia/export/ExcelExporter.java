package ru.mirea.avia.export;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.domain.Passenger;
import ru.mirea.avia.dto.AnalyticsDtos.StatisticsResponse;
import ru.mirea.avia.dto.ExportDtos.ExportFormat;
import ru.mirea.avia.util.UiText;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Экспорт в Excel (.xlsx) средствами Apache POI (FR-16, приложение Г ТЗ).
 *
 * <p>Книга содержит четыре листа: «Бронирования», «Пассажиры», «Рейсы» и «Статистика». Суммы
 * и даты записываются числами и датами, а не текстом, поэтому в Excel работают сортировка
 * и фильтры. Номера документов выводятся только маскированными (NFR-09).</p>
 */
public final class ExcelExporter extends AbstractFileExporter {
    private static final List<String> PASSENGER_HEADERS = List.of(
            "ID", "ФИО", "Дата рождения", "Тип документа", "Номер документа", "E-mail", "Телефон",
            "Число броней");
    private static final List<String> FLIGHT_HEADERS = List.of(
            "ID", "Номер рейса", "Авиакомпания", "Маршрут", "Вылет", "Прилёт", "Тип ВС", "Всего мест",
            "Занято мест", "Базовый тариф, руб.", "Статус");

    private final List<Passenger> passengers;
    private final List<Flight> flights;
    private final StatisticsResponse statistics;

    public ExcelExporter(List<Passenger> passengers, List<Flight> flights,
                         StatisticsResponse statistics, Clock clock) {
        super(ExportFormat.XLSX, clock);
        this.passengers = List.copyOf(passengers);
        this.flights = List.copyOf(flights);
        this.statistics = statistics;
    }

    @Override
    protected void write(List<Booking> bookings, Path file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            ExcelStyles styles = new ExcelStyles(workbook);
            writeBookings(workbook.createSheet("Бронирования"), styles, bookings);
            writePassengers(workbook.createSheet("Пассажиры"), styles, bookings);
            writeFlights(workbook.createSheet("Рейсы"), styles, bookings);
            new StatisticsSheetWriter(workbook.createSheet("Статистика"), styles).write(statistics);
            // Файл открывается только после сборки книги, чтобы ошибка заполнения не оставила пустой файл.
            try (OutputStream output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
    }

    private void writePassengers(Sheet sheet, ExcelStyles styles, List<Booking> bookings) {
        Map<Long, Long> bookingCounts = bookings.stream()
                .collect(Collectors.groupingBy(booking -> booking.getPassenger().getId(), Collectors.counting()));
        styles.header(sheet, PASSENGER_HEADERS);
        int rowIndex = 1;
        for (Passenger passenger : passengers) {
            Row row = sheet.createRow(rowIndex++);
            styles.number(row, 0, passenger.getId());
            styles.text(row, 1, passenger.getFullName());
            styles.date(row, 2, passenger.getBirthDate());
            styles.text(row, 3, passenger.getDocumentType().getTitle());
            styles.text(row, 4, passenger.getMaskedDocument());
            styles.text(row, 5, passenger.getEmail());
            styles.text(row, 6, passenger.getPhone());
            styles.number(row, 7, bookingCounts.getOrDefault(passenger.getId(), 0L));
        }
        styles.finishTable(sheet, PASSENGER_HEADERS.size(), rowIndex - 1);
    }

    private void writeFlights(Sheet sheet, ExcelStyles styles, List<Booking> bookings) {
        Map<Long, Long> occupiedSeats = bookings.stream()
                .filter(Booking::isActive)
                .collect(Collectors.groupingBy(booking -> booking.getFlight().getId(), Collectors.counting()));
        styles.header(sheet, FLIGHT_HEADERS);
        int rowIndex = 1;
        for (Flight flight : flights) {
            Row row = sheet.createRow(rowIndex++);
            styles.number(row, 0, flight.getId());
            styles.text(row, 1, flight.getFlightNumber());
            styles.text(row, 2, flight.getAirline());
            styles.text(row, 3, route(flight));
            styles.dateTime(row, 4, flight.getDepartureTime());
            styles.dateTime(row, 5, flight.getArrivalTime());
            styles.text(row, 6, flight.getAircraftType());
            styles.number(row, 7, flight.getTotalSeats());
            styles.number(row, 8, occupiedSeats.getOrDefault(flight.getId(), 0L));
            styles.money(row, 9, flight.getBasePrice());
            styles.text(row, 10, flight.getStatus().getTitle());
        }
        styles.finishTable(sheet, FLIGHT_HEADERS.size(), rowIndex - 1);
    }

    private static void writeBookings(Sheet sheet, ExcelStyles styles, List<Booking> bookings) {
        styles.header(sheet, BOOKING_HEADERS);
        int rowIndex = 1;
        for (Booking booking : bookings) {
            Row row = sheet.createRow(rowIndex++);
            styles.number(row, 0, booking.getId());
            styles.text(row, 1, booking.getBookingRef());
            styles.text(row, 2, booking.getPassenger().getFullName());
            styles.text(row, 3, document(booking.getPassenger()));
            styles.text(row, 4, flightTitle(booking));
            styles.text(row, 5, route(booking));
            styles.dateTime(row, 6, booking.getFlight().getDepartureTime());
            styles.text(row, 7, booking.getSeatNumber());
            styles.text(row, 8, booking.getFareClass().getTitle());
            styles.text(row, 9, UiText.bool(booking.isBaggageIncluded()));
            styles.money(row, 10, booking.getPrice());
            styles.text(row, 11, booking.getStatus().getTitle());
            styles.dateTime(row, 12, booking.getCreatedAt());
        }
        styles.finishTable(sheet, BOOKING_HEADERS.size(), rowIndex - 1);
    }
}
