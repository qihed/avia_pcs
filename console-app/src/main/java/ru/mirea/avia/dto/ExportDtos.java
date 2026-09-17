package ru.mirea.avia.dto;

import java.nio.file.Path;

/** DTO-контракты выгрузки данных (FR-16, FR-17). */
public final class ExportDtos {
    private ExportDtos() {
    }

    /** Поддерживаемые форматы выгрузки. */
    public enum ExportFormat { XLSX, CSV }

    /** Итог выгрузки: формат, полный путь к файлу и число выгруженных броней. */
    public record ExportResult(ExportFormat format, Path file, int rows) {
    }
}
