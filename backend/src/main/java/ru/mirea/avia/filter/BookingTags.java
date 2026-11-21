package ru.mirea.avia.filter;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Правила тегов брони. Тег хранится нормализованным — без лишних пробелов и в нижнем регистре,
 * поэтому «VIP», « vip » и «Vip» — один и тот же тег, а поиск по тегу — точное сравнение.
 */
public final class BookingTags {

    public static final int MAX_LENGTH = 40;
    public static final int MAX_PER_BOOKING = 10;

    /** Буквы, цифры, пробел, дефис, подчёркивание, точка; начинается с буквы или цифры. */
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]*$");

    private BookingTags() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Текст ошибки для нормализованного тега или null, если тег допустим. */
    public static String problem(String tag) {
        if (tag.isEmpty()) {
            return "Тег не может быть пустым";
        }
        if (tag.length() > MAX_LENGTH) {
            return "Тег «" + tag.substring(0, 20) + "…» длиннее " + MAX_LENGTH + " символов";
        }
        if (!ALLOWED.matcher(tag).matches()) {
            return "Тег «" + tag + "»: только буквы, цифры, пробел и знаки - _ .";
        }
        return null;
    }
}
