package ru.mirea.avia.util;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.mirea.avia.model.Booking;
import ru.mirea.avia.model.Passenger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Экспорт основных данных о бронированиях в Excel (.xlsx). */
public class ExcelExporter {

    // Формат даты и времени, который увидит пользователь в Excel.
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    // Названия столбцов первого листа Excel.
    private static final String[] HEADERS = {
            "ID", "Пассажир", "Документ", "Рейс", "Маршрут", "Вылет",
            "Место", "Класс", "Стоимость, руб.", "Статус"
    };

    /**
     * Создаёт Excel-файл по указанному пути и возвращает этот путь.
     * Список Booking уже получен из БД сервисом до вызова этого метода.
     */
    public Path export(List<Booking> bookings, Path path) throws IOException {
        // Создаём папку exports, если её ещё нет.
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        // try-with-resources автоматически закроет Workbook после работы.
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Бронирования");

            // Создаём стиль шапки: жирный белый текст на тёмном фоне.
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Первая строка — заголовки таблицы.
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                header.getCell(i).setCellStyle(headerStyle);
            }

            // Каждая бронь из List<Booking> становится отдельной строкой Excel.
            int rowIndex = 1;
            for (Booking booking : bookings) {
                Row row = sheet.createRow(rowIndex++);
                Passenger passenger = booking.getPassenger();

                row.createCell(0).setCellValue(booking.getId());
                row.createCell(1).setCellValue(passenger.getFullName());
                row.createCell(2).setCellValue(
                        passenger.getDocumentType().getTitle() + " " + passenger.getDocumentNumber());
                row.createCell(3).setCellValue(booking.getFlight().getFlightNumber());
                row.createCell(4).setCellValue(booking.getFlight().getRoute());
                row.createCell(5).setCellValue(DATE_TIME.format(booking.getFlight().getDepartureTime()));
                row.createCell(6).setCellValue(booking.getSeatNumber());
                row.createCell(7).setCellValue(booking.getFareClass().getTitle());

                // BigDecimal преобразуем в число, чтобы стоимость была числовой ячейкой Excel.
                row.createCell(8).setCellValue(booking.getPrice().doubleValue());
                row.createCell(9).setCellValue(booking.getStatus().getTitle());
            }

            // Закрепляем шапку: при прокрутке первая строка остаётся видимой.
            sheet.createFreezePane(0, 1);

            // Добавляем автофильтр по всем столбцам таблицы.
            if (!bookings.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, HEADERS.length - 1));
            }

            // Подбираем ширину столбцов по содержимому и немного расширяем их.
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
                int width = Math.min(sheet.getColumnWidth(i) + 800, 255 * 256);
                sheet.setColumnWidth(i, width);
            }

            // Записываем созданную книгу в файл на диске.
            try (OutputStream out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
        }

        return path;
    }

    /** Создаёт оформление строки заголовков. */
    private CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
