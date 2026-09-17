package ru.mirea.avia.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mirea.avia.dto.AnalyticsDtos.StatisticsResponse;
import ru.mirea.avia.dto.ExportDtos.ExportFormat;
import ru.mirea.avia.support.TestContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет книгу Excel: листы, типы ячеек, оформление заголовков и маскирование документов. */
class ExcelExporterTest {
    private final TestContext ctx = new TestContext().withDemoData();
    private final StatisticsResponse statistics = ctx.statisticsService.calculate();
    private final ExcelExporter exporter = new ExcelExporter(ctx.passengers.findAll(), ctx.flights.findAll(),
            statistics, ctx.clock);

    @Test
    void createsFourNamedSheets(@TempDir Path directory) throws IOException {
        Path file = exporter.export(ctx.bookings.findAll(), directory);
        assertThat(file.getFileName().toString()).isEqualTo("bookings_2026-09-16_15-00.xlsx");
        assertThat(file).isAbsolute().exists();
        assertThat(exporter.format()).isEqualTo(ExportFormat.XLSX);
        try (Workbook workbook = open(file)) {
            assertThat(IntStream.range(0, workbook.getNumberOfSheets()).mapToObj(workbook::getSheetName))
                    .containsExactly("Бронирования", "Пассажиры", "Рейсы", "Статистика");
        }
    }

    @Test
    void writesBookingRowsUnderFrozenBoldHeader(@TempDir Path directory) throws IOException {
        try (Workbook workbook = open(exporter.export(ctx.bookings.findAll(), directory))) {
            Sheet sheet = workbook.getSheet("Бронирования");
            assertThat(sheet.getLastRowNum()).isEqualTo(14);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(15);
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getPaneInformation().getHorizontalSplitTopRow()).isEqualTo((short) 1);
            assertThat(((XSSFSheet) sheet).getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:M15");
            assertThat(texts(sheet.getRow(0))).isEqualTo(AbstractFileExporter.BOOKING_HEADERS);
            assertThat(isBold(workbook, sheet.getRow(0).getCell(0))).isTrue();
            assertThat(isBold(workbook, sheet.getRow(1).getCell(1))).isFalse();
        }
    }

    @Test
    void writesTypedBookingCells(@TempDir Path directory) throws IOException {
        try (Workbook workbook = open(exporter.export(ctx.bookings.findAll(), directory))) {
            Row first = workbook.getSheet("Бронирования").getRow(1);
            assertThat(first.getCell(0).getNumericCellValue()).isEqualTo(1.0);
            assertThat(first.getCell(1).getStringCellValue()).isEqualTo("AB12CD");
            assertThat(first.getCell(2).getStringCellValue()).isEqualTo("Иванов Иван Иванович");
            assertThat(first.getCell(3).getStringCellValue()).isEqualTo("паспорт РФ 45 10 ****56");
            assertThat(first.getCell(4).getStringCellValue()).isEqualTo("SU1416 (Аэрофлот)");
            assertThat(first.getCell(5).getStringCellValue()).isEqualTo("SVO → LED");
            assertThat(first.getCell(9).getStringCellValue()).isEqualTo("Нет");
            assertThat(first.getCell(11).getStringCellValue()).isEqualTo("Перелёт выполнен");

            Cell price = first.getCell(10);
            assertThat(price.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(price.getNumericCellValue()).isEqualTo(6400.0);

            Cell departure = first.getCell(6);
            assertThat(departure.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(departure)).isTrue();
            assertThat(departure.getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 9, 8, 8, 40));
            assertThat(first.getCell(12).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 9, 1, 15, 0));
        }
    }

    @Test
    void writesPassengersWithBookingCount(@TempDir Path directory) throws IOException {
        try (Workbook workbook = open(exporter.export(ctx.bookings.findAll(), directory))) {
            Sheet sheet = workbook.getSheet("Пассажиры");
            assertThat(sheet.getLastRowNum()).isEqualTo(6);
            assertThat(isBold(workbook, sheet.getRow(0).getCell(0))).isTrue();
            Row ivanov = sheet.getRow(1);
            assertThat(ivanov.getCell(1).getStringCellValue()).isEqualTo("Иванов Иван Иванович");
            assertThat(ivanov.getCell(2).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(1985, 4, 12));
            assertThat(ivanov.getCell(3).getStringCellValue()).isEqualTo("паспорт РФ");
            assertThat(ivanov.getCell(4).getStringCellValue()).isEqualTo("45 10 ****56");
            assertThat(ivanov.getCell(7).getNumericCellValue()).isEqualTo(3.0);
            assertThat(sheet.getRow(6).getCell(7).getNumericCellValue()).isEqualTo(2.0);
        }
    }

    @Test
    void writesFlightsWithOccupiedSeats(@TempDir Path directory) throws IOException {
        try (Workbook workbook = open(exporter.export(ctx.bookings.findAll(), directory))) {
            Sheet sheet = workbook.getSheet("Рейсы");
            assertThat(sheet.getLastRowNum()).isEqualTo(8);
            Row su1420 = sheet.getRow(3);
            assertThat(su1420.getCell(1).getStringCellValue()).isEqualTo("SU1420");
            assertThat(su1420.getCell(3).getStringCellValue()).isEqualTo("SVO → LED");
            assertThat(su1420.getCell(4).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 9, 17, 8, 40));
            assertThat(su1420.getCell(7).getNumericCellValue()).isEqualTo(180.0);
            assertThat(su1420.getCell(8).getNumericCellValue()).isEqualTo(3.0);
            assertThat(su1420.getCell(9).getNumericCellValue()).isEqualTo(6400.0);
            assertThat(su1420.getCell(10).getStringCellValue()).isEqualTo("По расписанию");
            assertThat(sheet.getRow(5).getCell(8).getNumericCellValue()).isEqualTo(2.0);
        }
    }

    @Test
    void writesStatisticsSheet(@TempDir Path directory) throws IOException {
        try (Workbook workbook = open(exporter.export(ctx.bookings.findAll(), directory))) {
            Sheet sheet = workbook.getSheet("Статистика");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(texts(sheet.getRow(0))).containsExactly("Показатель", "Значение", "Доля, %");
            assertThat(valueOf(sheet, "ST-01 Всего пассажиров")).isEqualTo(6.0);
            assertThat(valueOf(sheet, "ST-02 Всего рейсов")).isEqualTo(8.0);
            assertThat(valueOf(sheet, "ST-03 Всего бронирований")).isEqualTo(14.0);
            assertThat(valueOf(sheet, "ST-06 Суммарная выручка, руб.")).isEqualTo(102390.0);
            assertThat(valueOf(sheet, "ST-07 Средняя стоимость билета, руб.")).isEqualTo(11376.67);
            assertThat(valueOf(sheet, "ST-11 Доля отменённых и просроченных броней")).isEqualTo(14.3);
            assertThat(valueOf(sheet, "  Оплачено")).isEqualTo(5.0);
            assertThat(valueOf(sheet, "  1. SU1420 SVO -> LED 17.09.2026")).isEqualTo(3.0);
            assertThat(valueOf(sheet, "  1. SVO -> LED")).isEqualTo(4.0);
        }
    }

    @Test
    void neverWritesFullDocumentNumbers(@TempDir Path directory) throws IOException {
        try (Workbook workbook = open(exporter.export(ctx.bookings.findAll(), directory))) {
            List<String> texts = StreamSupport.stream(workbook.spliterator(), false)
                    .flatMap(sheet -> StreamSupport.stream(sheet.spliterator(), false))
                    .flatMap(row -> StreamSupport.stream(row.spliterator(), false))
                    .filter(cell -> cell.getCellType() == CellType.STRING)
                    .map(Cell::getStringCellValue)
                    .toList();
            assertThat(texts).contains("VIII-MU-****56", "C0*****47");
            assertThat(String.join("\n", texts))
                    .doesNotContain("4510123456", "4512654321", "4512889967", "750123456", "VIII-MU-123456",
                            "C01X00T47");
        }
    }

    @Test
    void exportsEmptyBookingList(@TempDir Path directory) throws IOException {
        try (Workbook workbook = open(exporter.export(List.of(), directory))) {
            assertThat(workbook.getSheet("Бронирования").getLastRowNum()).isZero();
            assertThat(workbook.getSheet("Пассажиры").getRow(1).getCell(7).getNumericCellValue()).isZero();
        }
    }

    private static Workbook open(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return new XSSFWorkbook(input);
        }
    }

    private static List<String> texts(Row row) {
        return StreamSupport.stream(row.spliterator(), false)
                .map(Cell::getStringCellValue)
                .toList();
    }

    private static boolean isBold(Workbook workbook, Cell cell) {
        return workbook.getFontAt(cell.getCellStyle().getFontIndex()).getBold();
    }

    private static double valueOf(Sheet sheet, String label) {
        return StreamSupport.stream(sheet.spliterator(), false)
                .filter(row -> row.getCell(0) != null && label.equals(row.getCell(0).getStringCellValue()))
                .findFirst()
                .orElseThrow()
                .getCell(1)
                .getNumericCellValue();
    }
}
