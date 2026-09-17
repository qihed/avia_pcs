package ru.mirea.avia.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Тип документа пассажира с правилом проверки и маскирования номера. */
public enum DocumentType {
    PASSPORT_RF("паспорт РФ", "^\\d{10}$", "4510 123456 (10 цифр)"),
    INTERNATIONAL_PASSPORT("загранпаспорт", "^\\d{9}$", "750123456 (9 цифр)"),
    BIRTH_CERTIFICATE("свидетельство о рождении", "^[IVXLCDM]{1,4}-[A-ZА-ЯЁ]{2}-\\d{6}$",
            "VIII-МЮ-123456 (римское число, 2 буквы, 6 цифр)"),
    FOREIGN_DOCUMENT("иностранный документ", "^[A-Z0-9]{5,20}$", "C01X00T47 (5–20 латинских букв и цифр)");

    private final String title;
    private final Pattern pattern;
    private final String formatHint;

    DocumentType(String title, String regex, String formatHint) {
        this.title = title;
        this.pattern = Pattern.compile(regex);
        this.formatHint = formatHint;
    }

    /** Приводит номер к хранимому виду: без пробелов, в верхнем регистре. */
    public String normalize(String number) {
        if (number == null) return "";
        String compact = number.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return this == BIRTH_CERTIFICATE ? compact : compact.replace("-", "");
    }

    /** Проверяет, соответствует ли номер формату документа. */
    public boolean matches(String number) {
        return pattern.matcher(normalize(number)).matches();
    }

    /** Возвращает полный номер для детальной карточки: паспорт РФ — {@code 4510 123456}. */
    public String format(String number) {
        if (this == PASSPORT_RF && number != null && number.length() == 10) {
            return number.substring(0, 4) + " " + number.substring(4);
        }
        return number;
    }

    /**
     * Возвращает маскированный номер для списков (NFR-09).
     *
     * <p>Видны первые и последние символы, паспорт РФ выводится как {@code 45 12 ****67}.</p>
     */
    public String mask(String number) {
        if (number == null || number.length() < 5) return "****";
        String tail = number.substring(number.length() - 2);
        return switch (this) {
            case PASSPORT_RF -> number.substring(0, 2) + " " + number.substring(2, 4) + " ****" + tail;
            case BIRTH_CERTIFICATE -> number.substring(0, number.lastIndexOf('-') + 1) + "****" + tail;
            case INTERNATIONAL_PASSPORT, FOREIGN_DOCUMENT ->
                    number.substring(0, 2) + "*".repeat(number.length() - 4) + tail;
        };
    }

    public String getTitle() { return title; }
    public String getFormatHint() { return formatHint; }
}
