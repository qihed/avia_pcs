package ru.mirea.avia.util;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет разбор и вывод дат в формате интерфейса (NFR-16, E-102). */
class DateTimeFormatTest {
    @Test
    void parsesDates() {
        assertThat(DateTimeFormat.parseDate("06.09.2026")).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(DateTimeFormat.parseDate(" 06.09.2026 ")).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(DateTimeFormat.parseDate("29.02.2028")).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void parsesDateTimeWithExtraSpaces() {
        assertThat(DateTimeFormat.parseDateTime("06.09.2026 08:40")).isEqualTo(LocalDateTime.of(2026, 9, 6, 8, 40));
        assertThat(DateTimeFormat.parseDateTime(" 06.09.2026   08:40 "))
                .isEqualTo(LocalDateTime.of(2026, 9, 6, 8, 40));
    }

    @Test
    void rejectsNonexistentOrMisformattedDate() {
        assertThatThrownBy(() -> DateTimeFormat.parseDate("31.02.2026"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Такой даты не существует: 31.02.2026")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_102);
        assertThatThrownBy(() -> DateTimeFormat.parseDate("2026-09-06"))
                .hasMessage("Дата должна быть в формате дд.ММ.гггг")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_102);
        assertThatThrownBy(() -> DateTimeFormat.parseDate("29.02.2027")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> DateTimeFormat.parseDate("2026-09-06")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> DateTimeFormat.parseDate("6.9.2026")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> DateTimeFormat.parseDate(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsInvalidTime() {
        assertThatThrownBy(() -> DateTimeFormat.parseDateTime("06.09.2026 25:00"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Такой даты или времени не существует: 06.09.2026 25:00")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_102);
        assertThatThrownBy(() -> DateTimeFormat.parseDateTime("06.09.2026 8:40"))
                .hasMessage("Дата и время должны быть в формате дд.ММ.гггг ЧЧ:мм")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_102);
        assertThatThrownBy(() -> DateTimeFormat.parseDateTime("06.09.2026")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> DateTimeFormat.parseDateTime(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void formatsDatesForScreen() {
        assertThat(DateTimeFormat.format(LocalDate.of(2026, 9, 6))).isEqualTo("06.09.2026");
        assertThat(DateTimeFormat.format(LocalDateTime.of(2026, 9, 6, 8, 40))).isEqualTo("06.09.2026 08:40");
        assertThat(DateTimeFormat.fileStamp(LocalDateTime.of(2026, 9, 30, 18, 40))).isEqualTo("2026-09-30_18-40");
    }

    @Test
    void formatsMissingDateAsDash() {
        assertThat(DateTimeFormat.format((LocalDate) null)).isEqualTo("—");
        assertThat(DateTimeFormat.format((LocalDateTime) null)).isEqualTo("—");
    }

    @Test
    void exposesPatternsForPrompts() {
        assertThat(DateTimeFormat.DATE_PATTERN).isEqualTo("дд.ММ.гггг");
        assertThat(DateTimeFormat.DATE_TIME_PATTERN).isEqualTo("дд.ММ.гггг ЧЧ:мм");
    }
}
