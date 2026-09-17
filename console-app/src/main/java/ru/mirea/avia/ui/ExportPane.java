package ru.mirea.avia.ui;

import ru.mirea.avia.dto.ExportDtos.ExportFormat;
import ru.mirea.avia.dto.ExportDtos.ExportResult;
import ru.mirea.avia.export.Exporter;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.ExportService;
import ru.mirea.avia.util.UiText;

import java.util.List;

/**
 * Раздел «Экспорт данных» (FR-16, FR-17, UC-11).
 *
 * <p>Экран работает со ссылкой типа {@link Exporter} и не зависит от выбранного формата:
 * конкретную реализацию создаёт {@link ExportService}, что демонстрирует полиморфизм.</p>
 */
public final class ExportPane {
    private final ExportService exports;
    private final BookingService bookings;
    private final ConsoleIo io;
    private final InputPrompt prompt;
    private final CommandRunner runner;

    public ExportPane(ExportService exports, BookingService bookings, ConsoleIo io,
                      InputPrompt prompt, CommandRunner runner) {
        this.exports = exports;
        this.bookings = bookings;
        this.io = io;
        this.prompt = prompt;
        this.runner = runner;
    }

    /** Показывает меню выгрузки с каталогом назначения в заголовке. */
    public void show() {
        Menu.of("ЭКСПОРТ ДАННЫХ", io, prompt, runner)
                .header(this::printDirectory)
                .item("Экспорт в Excel (.xlsx) — 4 листа", "export.xlsx", () -> export(List.of(ExportFormat.XLSX)))
                .item("Экспорт бронирований в CSV (.csv)", "export.csv", () -> export(List.of(ExportFormat.CSV)))
                .item("Оба формата", "export.all", () -> export(List.of(ExportFormat.values())))
                .show();
    }

    private void printDirectory() {
        Ui.info(io, "Каталог выгрузки: " + exports.directory());
        io.println(ConsoleIo.line('-'));
    }

    private void export(List<ExportFormat> formats) {
        // BR-09: в файл должны попасть актуальные статусы, поэтому просроченные брони аннулируются до выгрузки.
        bookings.expireOutdated();
        io.println();
        for (ExportFormat format : formats) {
            Exporter exporter = exports.createExporter(format);
            Ui.info(io, "Выгрузка: " + UiText.exportFormat(exporter.format()) + "...");
            ExportResult result = exports.export(exporter);
            Ui.info(io, "Файл создан: " + result.file().toAbsolutePath().normalize());
            Ui.info(io, "Выгружено бронирований: " + result.rows());
        }
    }
}
