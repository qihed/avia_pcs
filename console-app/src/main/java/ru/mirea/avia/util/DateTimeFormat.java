package ru.mirea.avia.util;

import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/**
 * Разбирает и форматирует даты в формате интерфейса.
 *
 * <p>Оператор вводит и видит даты как {@code дд.ММ.гггг} и {@code дд.ММ.гггг ЧЧ:мм} (NFR-16).
 * Строгий режим разбора отклоняет несуществующие даты вроде {@code 31.02.2026}.</p>
 */
public final class DateTimeFormat {
    public static final String DATE_PATTERN = "дд.ММ.гггг";
    public static final String DATE_TIME_PATTERN = "дд.ММ.гггг ЧЧ:мм";

    private static final Pattern DATE_SHAPE = Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{4}$");
    private static final Pattern DATE_TIME_SHAPE = Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{4} \\d{2}:\\d{2}$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private DateTimeFormat() { }

    /** Разбирает дату {@code дд.ММ.гггг} и отклоняет неверный формат с кодом E-102. */
    public static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text.trim(), DATE);
        } catch (DateTimeParseException ex) {
            // Строка правильного вида, но несуществующая дата (31.02.2026) получает точное пояснение.
            String value = text == null ? "" : text.trim();
            throw new ValidationException(ErrorCode.E_102, DATE_SHAPE.matcher(value).matches()
                    ? "Такой даты не существует: " + value
                    : "Дата должна быть в формате " + DATE_PATTERN);
        }
    }

    /** Разбирает дату и время {@code дд.ММ.гггг ЧЧ:мм} и отклоняет неверный формат с кодом E-102. */
    public static LocalDateTime parseDateTime(String text) {
        // Лишние пробелы между датой и временем не считаются ошибкой ввода.
        String compact = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        try {
            return LocalDateTime.parse(compact, DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(ErrorCode.E_102, DATE_TIME_SHAPE.matcher(compact).matches()
                    ? "Такой даты или времени не существует: " + compact
                    : "Дата и время должны быть в формате " + DATE_TIME_PATTERN);
        }
    }

    /** Возвращает дату в виде {@code 06.09.2026} либо прочерк. */
    public static String format(LocalDate date) {
        if (date == null) return "—";
        return DATE.format(date);
    }

    /** Возвращает дату и время в виде {@code 06.09.2026 08:40} либо прочерк. */
    public static String format(LocalDateTime dateTime) {
        if (dateTime == null) return "—";
        return DATE_TIME.format(dateTime);
    }

    /** Возвращает метку времени для имени файла выгрузки: {@code 2026-09-30_18-40}. */
    public static String fileStamp(LocalDateTime dateTime) {
        return FILE_STAMP.format(dateTime);
    }
}
