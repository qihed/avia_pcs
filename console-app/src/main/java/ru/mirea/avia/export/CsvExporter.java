package ru.mirea.avia.export;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.dto.ExportDtos.ExportFormat;
import ru.mirea.avia.util.DateTimeFormat;
import ru.mirea.avia.util.MoneyFormat;
import ru.mirea.avia.util.UiText;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * Экспорт бронирований в CSV (FR-17).
 *
 * <p>Файл пишется в UTF-8 с BOM, чтобы Excel распознал кириллицу. Разделитель — точка с запятой,
 * строки завершаются CRLF, кавычки по RFC 4180 расставляет Apache Commons CSV. Текстовые значения
 * проходят через {@code safeCell}, поэтому Excel не примет их за формулы.</p>
 */
public final class CsvExporter extends AbstractFileExporter {
    private static final char BOM = '\ufeff';
    private static final CSVFormat FORMAT = CSVFormat.Builder.create()
            .setDelimiter(';')
            .setRecordSeparator("\r\n")
            .setQuoteMode(QuoteMode.MINIMAL)
            .get();

    public CsvExporter(Clock clock) {
        super(ExportFormat.CSV, clock);
    }

    @Override
    protected void write(List<Booking> bookings, Path file) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            // BOM записывается до создания CSVPrinter, иначе строка заголовков окажется перед BOM.
            writer.write(BOM);
            try (CSVPrinter printer = new CSVPrinter(writer, FORMAT)) {
                printer.printRecord(BOOKING_HEADERS);
                for (Booking booking : bookings) {
                    printer.printRecord(row(booking));
                }
            }
        }
    }

    private static List<String> row(Booking booking) {
        return List.of(
                String.valueOf(booking.getId()),
                safeCell(booking.getBookingRef()),
                safeCell(booking.getPassenger().getFullName()),
                safeCell(document(booking.getPassenger())),
                safeCell(flightTitle(booking)),
                safeCell(route(booking)),
                DateTimeFormat.format(booking.getFlight().getDepartureTime()),
                safeCell(booking.getSeatNumber()),
                booking.getFareClass().getTitle(),
                UiText.bool(booking.isBaggageIncluded()),
                MoneyFormat.formatPlain(booking.getPrice()),
                booking.getStatus().getTitle(),
                DateTimeFormat.format(booking.getCreatedAt()));
    }
}
