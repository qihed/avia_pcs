package ru.mirea.avia.util;

import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Форматирует и разбирает денежные суммы.
 *
 * <p>Суммы хранятся только в {@link BigDecimal} с двумя знаками после запятой и округлением
 * {@code HALF_UP} (BR-06). В интерфейсе они выводятся с разделителем разрядов, например
 * {@code 12 740,00} (NFR-16).</p>
 */
public final class MoneyFormat {
    public static final int SCALE = 2;

    private static final Pattern AMOUNT = Pattern.compile("^\\d{1,12}([.,]\\d{1,2})?$");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0]");

    private MoneyFormat() { }

    /** Возвращает сумму с разделителем разрядов: {@code 12740} → {@code 12 740,00}. */
    public static String format(BigDecimal amount) {
        if (amount == null) return "";
        return formatter("#,##0.00").format(amount);
    }

    /** Возвращает сумму с указанием валюты: {@code 12740} → {@code 12 740,00 руб.}. */
    public static String formatRub(BigDecimal amount) {
        return format(amount) + " руб.";
    }

    /** Возвращает сумму без разделителя разрядов для CSV: {@code 12740} → {@code 12740,00}. */
    public static String formatPlain(BigDecimal amount) {
        if (amount == null) return "";
        return formatter("0.00").format(amount);
    }

    /** Возвращает процент с одним знаком после запятой: {@code 14.3} → {@code 14,3 %}. */
    public static String formatPercent(BigDecimal percent) {
        if (percent == null) return "";
        return formatter("0.0").format(percent) + " %";
    }

    /** Округляет сумму до копеек по правилу {@code HALF_UP}. */
    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Разбирает сумму, введённую оператором, и отклоняет неверный формат с кодом E-103. */
    public static BigDecimal parse(String text) {
        // Оператор может отделять разряды пробелами, в том числе неразрывными, и писать запятую или точку.
        String compact = text == null ? "" : SPACES.matcher(text.trim()).replaceAll("");
        if (!AMOUNT.matcher(compact).matches()) {
            throw new ValidationException(ErrorCode.E_103, "Сумма должна быть числом, например 6400 или 6400,50");
        }
        return round(new BigDecimal(compact.replace(',', '.')));
    }

    private static DecimalFormat formatter(String pattern) {
        // DecimalFormat не потокобезопасен, поэтому экземпляр создаётся на каждый вызов.
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat(pattern, symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }
}
