package ru.mirea.avia.dto;

import java.util.List;

public record BookingCsvPreviewResponse(int totalRows, int validRows, int invalidRows, List<Row> rows) {
    public record Row(int line, String bookingCode, String status, Boolean archived, boolean valid, String error) {}
}
