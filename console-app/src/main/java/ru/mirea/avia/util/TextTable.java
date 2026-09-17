package ru.mirea.avia.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Строит выровненную текстовую таблицу для консоли (NFR-16).
 *
 * <p>Класс только формирует строки и ничего не печатает, поэтому его можно покрыть
 * unit-тестами. Ширина столбца вычисляется по всей выборке и ограничена 40 символами:
 * длинное значение обрезается с многоточием. При постраничном выводе (NFR-04) экран
 * передаёт в {@link #render(List, List)} текущую страницу и всю выборку, поэтому столбцы
 * не смещаются при переходе между страницами.</p>
 */
public final class TextTable<T> {
    private static final int MAX_CELL_WIDTH = 40;
    private static final int MIN_RULE_WIDTH = 10;
    private static final String MARGIN = "  ";
    private static final String SEPARATOR = " | ";
    private static final String ELLIPSIS = "…";

    private final List<Column<T>> columns = new ArrayList<>();

    private TextTable() { }

    /** Создаёт таблицу без столбцов; столбцы добавляются цепочкой вызовов. */
    public static <T> TextTable<T> create() {
        return new TextTable<>();
    }

    /** Добавляет столбец с выравниванием по левому краю. */
    public TextTable<T> column(String title, Function<T, String> value) {
        columns.add(new Column<>(title, value, false));
        return this;
    }

    /** Добавляет столбец с выравниванием по правому краю для чисел и сумм. */
    public TextTable<T> rightColumn(String title, Function<T, String> value) {
        columns.add(new Column<>(title, value, true));
        return this;
    }

    /** Возвращает строки таблицы: линия, заголовок, линия, строки данных и завершающая линия. */
    public List<String> render(List<T> rows) {
        return render(rows, rows);
    }

    /** Возвращает строки одной страницы, при этом ширина столбцов вычисляется по всей выборке. */
    public List<String> render(List<T> page, List<T> all) {
        List<String> headers = columns.stream()
                .map(Column::title)
                .toList();
        boolean[] right = new boolean[columns.size()];
        for (int i = 0; i < right.length; i++) {
            right[i] = columns.get(i).rightAlign();
        }
        return lines(headers, cells(page), widths(headers, cells(all)), right);
    }

    /** Возвращает строки таблицы из готовых текстовых значений; все столбцы выровнены влево. */
    public static List<String> renderRaw(List<String> headers, List<List<String>> rows) {
        return lines(headers, rows, widths(headers, rows), new boolean[headers.size()]);
    }

    private List<List<String>> cells(List<T> rows) {
        return rows.stream()
                .map(row -> columns.stream()
                        .map(column -> column.value().apply(row))
                        .toList())
                .toList();
    }

    private static List<String> lines(List<String> headers, List<List<String>> rows,
                                      int[] widths, boolean[] right) {
        String header = line(headers, widths, new boolean[widths.length]);
        String rule = "-".repeat(Math.max(header.length(), MIN_RULE_WIDTH));
        List<String> result = new ArrayList<>(rows.size() + 4);
        result.add(rule);
        result.add(header);
        result.add(rule);
        rows.forEach(row -> result.add(line(row, widths, right)));
        result.add(rule);
        return result;
    }

    private static int[] widths(List<String> headers, List<List<String>> rows) {
        int[] widths = new int[headers.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = text(headers.get(i)).length();
        }
        for (List<String> row : rows) {
            for (int i = 0; i < widths.length; i++) {
                widths[i] = Math.min(MAX_CELL_WIDTH, Math.max(widths[i], text(row.get(i)).length()));
            }
        }
        return widths;
    }

    private static String line(List<String> cells, int[] widths, boolean[] right) {
        List<String> parts = new ArrayList<>(widths.length);
        for (int i = 0; i < widths.length; i++) {
            String value = fit(text(cells.get(i)), widths[i]);
            String padding = " ".repeat(widths[i] - value.length());
            parts.add(right[i] ? padding + value : value + padding);
        }
        return (MARGIN + String.join(SEPARATOR, parts)).stripTrailing();
    }

    private static String fit(String value, int width) {
        if (value.length() <= width) return value;
        return value.substring(0, width - 1) + ELLIPSIS;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private record Column<R>(String title, Function<R, String> value, boolean rightAlign) {
    }
}
