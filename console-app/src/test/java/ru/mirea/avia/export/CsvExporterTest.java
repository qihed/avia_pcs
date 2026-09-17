package ru.mirea.avia.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.domain.Passenger;
import ru.mirea.avia.dto.ExportDtos.ExportFormat;
import ru.mirea.avia.error.ExportException;
import ru.mirea.avia.support.TestContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет выгрузку бронирований в CSV: кодировку, разделители, маскирование и защиту от формул. */
class CsvExporterTest {
    private static final String HEADER = "ID;Номер брони (PNR);Пассажир;Документ;Рейс;Маршрут;Вылет;Место;Класс;"
            + "Багаж;Стоимость, руб.;Статус;Создано";

    private final TestContext ctx = new TestContext().withDemoData();
    private final CsvExporter exporter = new CsvExporter(ctx.clock);

    @Test
    void writesUtf8WithBomAndCrlfLines(@TempDir Path directory) throws IOException {
        byte[] bytes = Files.readAllBytes(exporter.export(ctx.bookings.findAll(), directory));
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String content = new String(bytes, StandardCharsets.UTF_8);
        assertThat(content).startsWith("﻿" + HEADER + "\r\n").endsWith("\r\n");
        assertThat(content.split("\r\n")).hasSize(15);
        assertThat(content.replace("\r\n", "")).doesNotContain("\n", "\r");
    }

    @Test
    void namesFileByExportTime(@TempDir Path directory) throws IOException {
        Path file = exporter.export(List.of(), directory.resolve("nested"));
        assertThat(file.getFileName().toString()).isEqualTo("bookings_2026-09-16_15-00.csv");
        assertThat(file).isAbsolute().exists().hasParent(directory.resolve("nested"));
        assertThat(lines(file)).containsExactly("﻿" + HEADER);
        assertThat(exporter.format()).isEqualTo(ExportFormat.CSV);
    }

    @Test
    void writesBookingRowsWithMaskedDocuments(@TempDir Path directory) throws IOException {
        List<String> lines = lines(exporter.export(ctx.bookings.findAll(), directory));
        assertThat(lines.get(1))
                .startsWith("1;AB12CD;Иванов Иван Иванович;паспорт РФ 45 10 ****56;SU1416 (Аэрофлот);")
                .contains(";6400,00;Перелёт выполнен;")
                .isEqualTo("1;AB12CD;Иванов Иван Иванович;паспорт РФ 45 10 ****56;SU1416 (Аэрофлот);"
                        + "SVO → LED;08.09.2026 08:40;12A;Эконом;Нет;6400,00;Перелёт выполнен;01.09.2026 15:00");
        assertThat(lines.get(6)).isEqualTo("6;RT58NB;Соколов Артём Дмитриевич;"
                + "свидетельство о рождении VIII-MU-****56;SU1108 (Аэрофлот);SVO → AER;23.09.2026 19:05;21D;"
                + "Эконом;Нет;8300,00;Оплачено;13.09.2026 15:00");
        assertThat(lines.get(7)).contains(";Бизнес;Да;20750,00;Оплачено;");
        assertThat(lines.get(14)).startsWith("14;WE81YT;").contains(";Бронь просрочена;");
        assertThat(String.join("\n", lines))
                .doesNotContain("4510123456", "4512654321", "4512889967", "750123456", "VIII-MU-123456",
                        "C01X00T47", "ivanov@example.com");
    }

    @Test
    void neutralisesFormulaLikeValuesInFile(@TempDir Path directory) throws IOException {
        Passenger attacker = ctx.passenger("=1+2", "Ева", null, "1990-01-01", DocumentType.FOREIGN_DOCUMENT,
                "X12345", "formula");
        ctx.booking("@EVIL1", attacker.getId(), 3, "-1A", FareClass.ECONOMY, false, "6400",
                BookingStatus.CREATED, 1);
        List<String> lines = lines(exporter.export(ctx.bookings.findAll(), directory));
        assertThat(lines).hasSize(16);
        assertThat(lines.get(15))
                .startsWith("15;'@EVIL1;'=1+2 Ева;иностранный документ X1**45;SU1420 (Аэрофлот);")
                .contains(";'-1A;");
    }

    @Test
    void safeCellPrefixesFormulaStarts() {
        assertThat(AbstractFileExporter.safeCell("=SUM(A1:A9)")).isEqualTo("'=SUM(A1:A9)");
        assertThat(AbstractFileExporter.safeCell("+79161234567")).isEqualTo("'+79161234567");
        assertThat(AbstractFileExporter.safeCell("-5")).isEqualTo("'-5");
        assertThat(AbstractFileExporter.safeCell("@cmd")).isEqualTo("'@cmd");
        assertThat(AbstractFileExporter.safeCell("\tX")).isEqualTo("'\tX");
        assertThat(AbstractFileExporter.safeCell("\rX")).isEqualTo("'\rX");
        assertThat(AbstractFileExporter.safeCell("Иванов = 1")).isEqualTo("Иванов = 1");
        assertThat(AbstractFileExporter.safeCell("")).isEmpty();
        assertThat(AbstractFileExporter.safeCell(null)).isEmpty();
    }

    @Test
    void quotesValuesContainingDelimiter(@TempDir Path directory) throws IOException {
        Passenger passenger = ctx.passenger("Смит;Джонс", "Анна", null, "1990-01-01",
                DocumentType.FOREIGN_DOCUMENT, "Y12345", "smith");
        ctx.booking("QT11QT", passenger.getId(), 3, "2A", FareClass.ECONOMY, false, "6400",
                BookingStatus.CREATED, 1);
        List<String> lines = lines(exporter.export(ctx.bookings.findAll(), directory));
        assertThat(lines.get(15)).startsWith("15;QT11QT;\"Смит;Джонс Анна\";");
    }

    @Test
    void reportsUnwritableTarget(@TempDir Path directory) throws IOException {
        Path blocker = Files.writeString(directory.resolve("busy"), "x");
        assertThatThrownBy(() -> exporter.export(List.of(), blocker))
                .isInstanceOf(ExportException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-601")
                .hasMessageStartingWith("Не удалось создать файл: нет прав на запись в каталог");
    }

    private static List<String> lines(Path file) throws IOException {
        return List.of(Files.readString(file).split("\r\n"));
    }
}
