package ru.mirea.avia.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.mirea.avia.model.Booking;
import ru.mirea.avia.model.Passenger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Экспорт бронирований в Excel (.xlsx). */
public class ExcelExporter {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String[] HEADERS = {"ID", "Пассажир", "Документ", "Рейс", "Маршрут", "Вылет",
            "Место", "Класс", "Стоимость, руб.", "Статус"};

    public Path export(List<Booking> bookings, Path path) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Бронирования");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) header.createCell(i).setCellValue(HEADERS[i]);

            int rowIndex = 1;
            for (Booking booking : bookings) {
                Row row = sheet.createRow(rowIndex++);
                Passenger passenger = booking.getPassenger();
                row.createCell(0).setCellValue(booking.getId());
                row.createCell(1).setCellValue(passenger.getFullName());
                row.createCell(2).setCellValue(passenger.getDocumentType().getTitle() + " " + passenger.getDocumentNumber());
                row.createCell(3).setCellValue(booking.getFlight().getFlightNumber());
                row.createCell(4).setCellValue(booking.getFlight().getRoute());
                row.createCell(5).setCellValue(DATE_TIME.format(booking.getFlight().getDepartureTime()));
                row.createCell(6).setCellValue(booking.getSeatNumber());
                row.createCell(7).setCellValue(booking.getFareClass().getTitle());
                row.createCell(8).setCellValue(booking.getPrice().doubleValue());
                row.createCell(9).setCellValue(booking.getStatus().getTitle());
            }
            for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);

            try (OutputStream out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
        }
        return path;
    }
}
