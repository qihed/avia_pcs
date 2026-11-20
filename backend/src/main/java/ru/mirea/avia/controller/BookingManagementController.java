package ru.mirea.avia.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.mirea.avia.dto.*;
import ru.mirea.avia.service.BookingManagementService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings/management")
public class BookingManagementController {
    private final BookingManagementService service;
    public BookingManagementController(BookingManagementService service) { this.service = service; }

    @GetMapping public List<BookingManagementRow> list() { return service.list(); }
    @PostMapping("/bulk-update") public Map<String,Integer> bulkUpdate(@Valid @RequestBody BookingBulkUpdateRequest request) {
        return Map.of("updated", service.updateSelected(request));
    }
    @GetMapping("/csv") public BookingCsvExportResponse exportCsv() { return service.exportCsv(); }
    @PostMapping("/csv/preview") public BookingCsvPreviewResponse previewCsv(@Valid @RequestBody BookingCsvRequest request) {
        return service.previewCsv(request.csv());
    }
    @PostMapping("/csv/apply") public Map<String,Integer> applyCsv(@Valid @RequestBody BookingCsvRequest request) {
        return Map.of("updated", service.applyCsv(request.csv()));
    }
}
