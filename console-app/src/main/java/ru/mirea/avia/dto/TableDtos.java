package ru.mirea.avia.dto;

import java.util.List;

/** DTO-контракты прямого просмотра таблиц базы данных (FR-18, UC-12). */
public final class TableDtos {
    private TableDtos() {
    }

    /** Таблицы, доступные для просмотра в исходном виде. */
    public enum DatabaseTable { PASSENGERS, FLIGHTS, BOOKINGS }

    /** Страница строк таблицы: имена столбцов и значения в текстовом виде. */
    public record TablePage(
            DatabaseTable table,
            List<String> columns,
            PageResponse<List<String>> rows) {
    }
}
