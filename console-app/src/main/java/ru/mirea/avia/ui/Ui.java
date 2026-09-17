package ru.mirea.avia.ui;

import ru.mirea.avia.dto.ErrorReport;
import ru.mirea.avia.dto.PageResponse;
import ru.mirea.avia.util.TextTable;
import ru.mirea.avia.util.UiText;

import java.util.List;

/**
 * Общие элементы консольных экранов: заголовки, сообщения, поля карточек и таблицы.
 *
 * <p>Экраны не форматируют сообщения сами, поэтому ошибки и подсказки выглядят одинаково
 * во всех разделах. Списки выводятся страницами по {@value #PAGE_SIZE} строк (NFR-04),
 * а ширина столбцов считается по всей выборке, чтобы таблица не смещалась.</p>
 */
public final class Ui {
    public static final int PAGE_SIZE = 20;
    private static final int SHORT_ID_LENGTH = 8;

    private Ui() { }

    /** Печатает заголовок экрана между двойными линиями. */
    public static void title(ConsoleIo io, String text) {
        io.println();
        io.println(ConsoleIo.line('='));
        io.println("  " + text);
        io.println(ConsoleIo.line('='));
    }

    /** Печатает заголовок диалога с напоминанием об отмене вводом 0. */
    public static void section(ConsoleIo io, String text) {
        io.println();
        io.println("  --- " + text + " ---   (0 — отмена и возврат в меню)");
        io.println();
    }

    /** Печатает отчёт об ошибке команды; для ошибок E-5xx добавляет короткий correlation ID. */
    public static void error(ConsoleIo io, ErrorReport report) {
        String text = "  Операция не выполнена [" + report.code() + "]: " + report.message();
        // По correlation ID администратор находит в журнале технические подробности ошибки БД.
        if (report.code().startsWith("E-5") && report.correlationId() != null) {
            text = text + " (correlation ID: " + shortId(report.correlationId()) + ")";
        }
        io.println(text);
    }

    /** Печатает сообщение об ошибке ввода или проверки. */
    public static void error(ConsoleIo io, String message) {
        io.println("  Ошибка: " + message);
    }

    /** Печатает информационное сообщение. */
    public static void info(ConsoleIo io, String message) {
        io.println("  " + message);
    }

    /** Печатает сообщение о пустом результате. */
    public static void empty(ConsoleIo io, String message) {
        io.println("  " + message);
    }

    /** Печатает строку карточки в виде «подпись: значение» с выравниванием подписи. */
    public static void field(ConsoleIo io, String label, String value) {
        io.println("   %-20s: %s".formatted(label, value));
    }

    /** Печатает готовые строки, например результат {@link TextTable#render(List)}. */
    public static void lines(ConsoleIo io, List<String> lines) {
        lines.forEach(io::println);
    }

    /** Печатает таблицу постранично и завершает её строкой «Всего записей». */
    public static <T> void table(ConsoleIo io, TextTable<T> table, List<T> rows) {
        if (rows.isEmpty()) {
            empty(io, "Записей не найдено");
            return;
        }

        int page = 0;
        PageResponse<T> slice = PageResponse.slice(rows, page, PAGE_SIZE);
        lines(io, table.render(slice.content(), rows));
        while (!slice.last() && nextPage(io, page + 1, slice.totalPages(), rows.size())) {
            page++;
            slice = PageResponse.slice(rows, page, PAGE_SIZE);
            lines(io, table.render(slice.content(), rows));
        }
        info(io, "Всего записей: " + rows.size());
    }

    /** Спрашивает, показать ли следующую страницу; возвращает {@code false}, если оператор ввёл 0. */
    public static boolean nextPage(ConsoleIo io, int page, int totalPages, long total) {
        io.print("  Страница " + page + " из " + totalPages + " · " + UiText.records(total)
                + ". Enter — следующая страница, 0 — завершить просмотр: ");
        return !InputPrompt.CANCEL.equals(io.readLine().trim());
    }

    private static String shortId(String correlationId) {
        return correlationId.substring(0, Math.min(SHORT_ID_LENGTH, correlationId.length()));
    }
}
