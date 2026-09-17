package ru.mirea.avia.ui;

import ru.mirea.avia.dto.PageResponse;
import ru.mirea.avia.dto.TableDtos.DatabaseTable;
import ru.mirea.avia.dto.TableDtos.TablePage;
import ru.mirea.avia.service.TableViewService;
import ru.mirea.avia.util.TextTable;
import ru.mirea.avia.util.UiText;

import java.util.List;

/**
 * Раздел «Вывести таблицы базы данных»: строки таблиц в исходном виде (FR-18, UC-12).
 *
 * <p>Таблица выводится страницами по {@value TableViewService#PAGE_SIZE} строк (NFR-04):
 * следующая страница загружается из БД только по запросу оператора. Номера документов
 * маскирует сервис (NFR-09).</p>
 */
public final class TablesPane {
    private final TableViewService tables;
    private final ConsoleIo io;
    private final InputPrompt prompt;
    private final CommandRunner runner;

    public TablesPane(TableViewService tables, ConsoleIo io, InputPrompt prompt, CommandRunner runner) {
        this.tables = tables;
        this.io = io;
        this.prompt = prompt;
        this.runner = runner;
    }

    /** Показывает меню раздела, пока оператор не выберет пункт 0. */
    public void show() {
        Menu.of("ТАБЛИЦЫ БАЗЫ ДАННЫХ", io, prompt, runner)
                .item(UiText.table(DatabaseTable.PASSENGERS), "tables.passengers",
                        () -> print(DatabaseTable.PASSENGERS))
                .item(UiText.table(DatabaseTable.FLIGHTS), "tables.flights",
                        () -> print(DatabaseTable.FLIGHTS))
                .item(UiText.table(DatabaseTable.BOOKINGS), "tables.bookings",
                        () -> print(DatabaseTable.BOOKINGS))
                .show();
    }

    private void print(DatabaseTable table) {
        io.println();
        Ui.info(io, heading(table));
        TablePage first = tables.page(table, 0);
        if (first.rows().content().isEmpty()) {
            Ui.empty(io, "Таблица пуста");
            return;
        }

        PageResponse<List<String>> rows = printPage(first);
        while (!rows.last() && Ui.nextPage(io, rows.page() + 1, rows.totalPages(), rows.totalElements())) {
            rows = printPage(tables.page(table, rows.page() + 1));
        }
        Ui.info(io, "Всего строк в таблице: " + rows.totalElements());
    }

    private PageResponse<List<String>> printPage(TablePage page) {
        Ui.lines(io, TextTable.renderRaw(page.columns(), page.rows().content()));
        return page.rows();
    }

    private static String heading(DatabaseTable table) {
        // Подпись пункта меню «passengers — Пассажиры» в заголовке выводится как «passengers (Пассажиры)».
        return "Таблица " + UiText.table(table).replace(" — ", " (") + ")";
    }
}
