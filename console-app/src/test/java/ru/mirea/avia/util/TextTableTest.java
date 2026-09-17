package ru.mirea.avia.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет выравнивание, обрезку и ширину столбцов консольной таблицы (NFR-16). */
class TextTableTest {
    private final TextTable<List<String>> bookings = TextTable.<List<String>>create()
            .column("PNR", row -> row.get(0))
            .column("Пассажир", row -> row.get(1))
            .rightColumn("Стоимость", row -> row.get(2));

    @Test
    void rendersRulesHeaderAndAlignedRows() {
        List<String> lines = bookings.render(List.of(
                List.of("AB12CD", "Иванов И. И.", "6 400,00"),
                List.of("NR61WS", "Петрова А. С.", "15 780,00")));

        assertThat(lines).containsExactly(
                "-".repeat(36),
                "  PNR    | Пассажир      | Стоимость",
                "-".repeat(36),
                "  AB12CD | Иванов И. И.  |  6 400,00",
                "  NR61WS | Петрова А. С. | 15 780,00",
                "-".repeat(36));
    }

    @Test
    void alignsRightColumnValuesButNotHeader() {
        TextTable<String> table = TextTable.<String>create().rightColumn("N", value -> value);

        assertThat(table.render(List.of("100", "7"))).containsExactly(
                "-".repeat(10),
                "  N",
                "-".repeat(10),
                "  100",
                "    7",
                "-".repeat(10));
    }

    @Test
    void truncatesLongValueWithEllipsis() {
        TextTable<String> table = TextTable.<String>create().column("Имя", value -> value);

        List<String> lines = table.render(List.of("А".repeat(45), "Б".repeat(40)));

        assertThat(lines.get(3)).isEqualTo("  " + "А".repeat(39) + "…");
        assertThat(lines.get(4)).isEqualTo("  " + "Б".repeat(40));
        assertThat(lines.get(1)).isEqualTo("  Имя");
    }

    @Test
    void computesWidthsOverAllRows() {
        TextTable<List<String>> table = TextTable.<List<String>>create()
                .column("Город", row -> row.get(0))
                .rightColumn("Код", row -> row.get(1));
        List<List<String>> all = List.of(List.of("Тверь", "TX"), List.of("Константинопольский", "IST"));

        List<String> lines = table.render(all.subList(0, 1), all);

        assertThat(lines).hasSize(5);
        assertThat(lines.get(1)).isEqualTo("  Город" + " ".repeat(14) + " | Код");
        assertThat(lines.get(3)).isEqualTo("  Тверь" + " ".repeat(14) + " |  TX");
        assertThat(lines.get(0)).hasSize(27);
    }

    @Test
    void rendersEmptyTableWithHeaderOnly() {
        TextTable<String> table = TextTable.<String>create().column("ID", value -> value);

        assertThat(table.render(List.of())).containsExactly("-".repeat(10), "  ID", "-".repeat(10), "-".repeat(10));
    }

    @Test
    void rendersRawRowsLeftAlignedWithEmptyNulls() {
        List<String> lines = TextTable.renderRaw(List.of("id", "last_name"),
                List.of(List.of("1", "Иванов"), Arrays.asList("22", null)));

        assertThat(lines).containsExactly(
                "-".repeat(16),
                "  id | last_name",
                "-".repeat(16),
                "  1  | Иванов",
                "  22 |",
                "-".repeat(16));
    }
}
