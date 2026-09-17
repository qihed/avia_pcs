package ru.mirea.avia.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mirea.avia.dto.ExportDtos.ExportFormat;
import ru.mirea.avia.dto.ExportDtos.ExportResult;
import ru.mirea.avia.export.CsvExporter;
import ru.mirea.avia.export.ExcelExporter;
import ru.mirea.avia.export.Exporter;
import ru.mirea.avia.jdbc.Transactions;
import ru.mirea.avia.support.TestContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет выбор экспортёра по формату и выгрузку всех броней в каталог из конфигурации. */
class ExportServiceTest {
    private final TestContext ctx = new TestContext().withDemoData();

    @Test
    void createsExporterForEachFormat(@TempDir Path directory) {
        ExportService service = service(directory);
        Exporter excel = service.createExporter(ExportFormat.XLSX);
        Exporter csv = service.createExporter(ExportFormat.CSV);
        assertThat(excel).isInstanceOf(ExcelExporter.class);
        assertThat(excel.format()).isEqualTo(ExportFormat.XLSX);
        assertThat(csv).isInstanceOf(CsvExporter.class);
        assertThat(csv.format()).isEqualTo(ExportFormat.CSV);
        for (ExportFormat format : ExportFormat.values()) {
            assertThat(service.createExporter(format).format()).isEqualTo(format);
        }
    }

    @Test
    void exportsAllBookingsToCsv(@TempDir Path directory) throws IOException {
        ExportService service = service(directory.resolve("exports"));
        ExportResult result = service.export(service.createExporter(ExportFormat.CSV));
        assertThat(result.format()).isEqualTo(ExportFormat.CSV);
        assertThat(result.rows()).isEqualTo(14);
        assertThat(result.file()).isAbsolute().exists().hasFileName("bookings_2026-09-16_15-00.csv");
        assertThat(result.file().getParent()).isEqualTo(directory.resolve("exports"));
        assertThat(Files.readAllLines(result.file())).hasSize(15);
    }

    @Test
    void exportsAllBookingsToExcel(@TempDir Path directory) throws IOException {
        ExportService service = service(directory);
        ExportResult result = service.export(service.createExporter(ExportFormat.XLSX));
        assertThat(result.format()).isEqualTo(ExportFormat.XLSX);
        assertThat(result.rows()).isEqualTo(14);
        assertThat(result.file()).isAbsolute().exists().hasFileName("bookings_2026-09-16_15-00.xlsx");
        assertThat(Files.size(result.file())).isPositive();
    }

    @Test
    void returnsAbsoluteNormalizedDirectory() {
        ExportService service = service(Path.of("exports", "..", "exports"));
        assertThat(service.directory()).isAbsolute().isEqualTo(Path.of("exports").toAbsolutePath());
    }

    private ExportService service(Path directory) {
        return new ExportService(ctx.passengers, ctx.flights, ctx.bookings, ctx.statisticsService,
                Transactions.direct(), directory, ctx.clock);
    }
}
