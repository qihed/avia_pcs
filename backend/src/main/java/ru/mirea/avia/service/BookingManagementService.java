package ru.mirea.avia.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.avia.dto.*;
import ru.mirea.avia.error.BadRequestException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

@Service
public class BookingManagementService {
    private static final Set<String> STATUSES = Set.of("CREATED", "PAID", "CHECKED_IN", "COMPLETED", "CANCELLED", "EXPIRED");
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public BookingManagementService(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    public List<BookingManagementRow> list() {
        return jdbc.query("""
                SELECT b.id, b.booking_code, b.status, b.price, b.archived,
                       p.last_name || ' ' || p.first_name passenger, f.flight_number
                FROM bookings b JOIN passengers p ON p.id=b.passenger_id JOIN flights f ON f.id=b.flight_id
                ORDER BY b.created_at DESC
                """, (rs, row) -> new BookingManagementRow(rs.getObject("id", UUID.class), rs.getString("booking_code"),
                rs.getString("passenger"), rs.getString("flight_number"), rs.getString("status"),
                rs.getBigDecimal("price"), rs.getBoolean("archived")));
    }

    @Transactional
    public int updateSelected(BookingBulkUpdateRequest request) {
        if (request.status() == null && request.archived() == null) throw new BadRequestException("Укажите status или archived");
        int updated = 0;
        for (UUID id : request.bookingIds()) {
            if (request.status() != null && request.archived() != null)
                updated += jdbc.update("UPDATE bookings SET status=?, archived=?, updated_at=now(), version=version+1 WHERE id=?",
                        request.status(), request.archived(), id);
            else if (request.status() != null)
                updated += jdbc.update("UPDATE bookings SET status=?, updated_at=now(), version=version+1 WHERE id=?", request.status(), id);
            else
                updated += jdbc.update("UPDATE bookings SET archived=?, updated_at=now(), version=version+1 WHERE id=?", request.archived(), id);
        }
        audit.record("BOOKING_BULK_UPDATE", "BOOKING", null, "Массово обновлено бронирований: " + updated);
        return updated;
    }

    public BookingCsvExportResponse exportCsv() {
        StringWriter out = new StringWriter();
        try (CSVPrinter csv = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader("booking_code", "status", "archived").get())) {
            for (BookingManagementRow row : list()) csv.printRecord(row.bookingCode(), row.status(), row.archived());
        } catch (IOException e) { throw new IllegalStateException("Не удалось сформировать CSV", e); }
        audit.record("BOOKING_CSV_EXPORT", "BOOKING", null, "Экспорт бронирований в CSV");
        return new BookingCsvExportResponse("bookings.csv", out.toString());
    }

    public BookingCsvPreviewResponse previewCsv(String csv) {
        List<BookingCsvPreviewResponse.Row> rows = parse(csv);
        int valid = (int) rows.stream().filter(BookingCsvPreviewResponse.Row::valid).count();
        return new BookingCsvPreviewResponse(rows.size(), valid, rows.size() - valid, rows);
    }

    @Transactional
    public int applyCsv(String csv) {
        List<BookingCsvPreviewResponse.Row> rows = parse(csv);
        if (rows.stream().anyMatch(row -> !row.valid())) throw new BadRequestException("CSV содержит ошибки. Сначала выполните preview");
        int updated = 0;
        for (BookingCsvPreviewResponse.Row row : rows)
            updated += jdbc.update("UPDATE bookings SET status=?, archived=?, updated_at=now(), version=version+1 WHERE booking_code=?",
                    row.status(), row.archived(), row.bookingCode());
        audit.record("BOOKING_CSV_APPLY", "BOOKING", null, "Из CSV обновлено бронирований: " + updated);
        return updated;
    }

    private List<BookingCsvPreviewResponse.Row> parse(String text) {
        if (text == null || text.isBlank()) throw new BadRequestException("CSV пуст");
        List<BookingCsvPreviewResponse.Row> result = new ArrayList<>();
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).get()
                .parse(new StringReader(text))) {
            int line = 1;
            for (CSVRecord record : parser) {
                line++;
                String code = field(record, "booking_code").toUpperCase(Locale.ROOT);
                String status = field(record, "status").toUpperCase(Locale.ROOT);
                String archivedText = field(record, "archived").toLowerCase(Locale.ROOT);
                String error = null;
                Boolean archived = null;
                if (!code.matches("[A-Z0-9]{6}")) error = "Некорректный booking_code";
                else if (!STATUSES.contains(status)) error = "Неизвестный status";
                else if (!archivedText.equals("true") && !archivedText.equals("false")) error = "archived должен быть true или false";
                else archived = Boolean.valueOf(archivedText);
                if (error == null) {
                    Integer found = jdbc.queryForObject("SELECT count(*) FROM bookings WHERE booking_code=?", Integer.class, code);
                    if (found == null || found == 0) error = "Бронь не найдена";
                }
                result.add(new BookingCsvPreviewResponse.Row(line, code, status, archived, error == null, error));
            }
        } catch (IOException | IllegalArgumentException e) { throw new BadRequestException("Некорректный CSV: " + e.getMessage()); }
        return result;
    }

    private String field(CSVRecord record, String name) {
        try { return Optional.ofNullable(record.get(name)).orElse("").trim(); }
        catch (IllegalArgumentException e) { throw new BadRequestException("Нет обязательной колонки: " + name); }
    }
}
