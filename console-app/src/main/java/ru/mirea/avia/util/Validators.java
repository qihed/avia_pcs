package ru.mirea.avia.util;

import java.util.regex.Pattern;

/** Общие правила проверки ФИО и e-mail для консоли и сервиса. */
public final class Validators {
    /** Слово ФИО: первая буква заглавная, остальные строчные; двойные части через дефис. */
    private static final Pattern NAME = Pattern.compile("\\p{Lu}\\p{Ll}*(-\\p{Lu}\\p{Ll}*)*");
    /** E-mail: текст до @, домен и окончание .com или .ru. */
    private static final Pattern EMAIL =
            Pattern.compile("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)*\\.(com|ru)", Pattern.CASE_INSENSITIVE);

    /** Телефон: только цифры, от 10 до 15 штук (например, 89161234567). */
    private static final Pattern PHONE = Pattern.compile("\\d{10,15}");

    private Validators() {
    }

    public static boolean isValidName(String value) {
        return value != null && NAME.matcher(value).matches();
    }

    public static boolean isValidEmail(String value) {
        return value != null && EMAIL.matcher(value).matches();
    }

    public static boolean isValidPhone(String value) {
        return value != null && PHONE.matcher(value).matches();
    }
}
