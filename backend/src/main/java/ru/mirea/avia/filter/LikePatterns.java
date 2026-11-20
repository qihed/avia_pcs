package ru.mirea.avia.filter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Подготовка текста поиска для LIKE: значение передаётся параметром, спецсимволы % и _ экранируются. */
public final class LikePatterns {

    /** Символ экранирования, его же передаём в cb.like(..., ESCAPE). */
    public static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    /** "%текст%" в нижнем регистре; % и _ из пользовательского ввода ищутся как обычные символы. */
    public static String contains(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String escaped = lower.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** Разбивает строку поиска на слова; пустая строка — пустой список. */
    public static List<String> words(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.trim().split("\\s+")).filter(w -> !w.isEmpty()).toList();
    }
}
