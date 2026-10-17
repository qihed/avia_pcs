package ru.mirea.avia.util;

import ru.mirea.avia.model.DocumentType;

import java.util.List;
import java.util.regex.Pattern;

/** Общие правила проверки полей для консоли, JavaFX-форм и сервисов. */
public final class Validators {
    /** Слово ФИО: первая буква заглавная, остальные строчные; двойные части через дефис. */
    private static final Pattern NAME = Pattern.compile("\\p{Lu}\\p{Ll}*(-\\p{Lu}\\p{Ll}*)*");
    /** Почтовые сервисы, адреса которых принимаются. */
    private static final List<String> EMAIL_DOMAINS = List.of(
            "gmail.com", "mail.ru", "bk.ru", "inbox.ru", "list.ru", "yandex.ru", "yandex.com", "ya.ru",
            "rambler.ru", "outlook.com", "hotmail.com", "live.com", "yahoo.com", "icloud.com", "me.com",
            "proton.me", "protonmail.com");
    /** E-mail: имя из латинских букв, цифр и . _ % + -, затем @ и домен из списка известных почтовых сервисов. */
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@("
            + String.join("|", EMAIL_DOMAINS.stream().map(Pattern::quote).toList()) + ")",
            Pattern.CASE_INSENSITIVE);
    /** Сообщение об ошибке e-mail для консоли и форм. */
    public static final String EMAIL_HINT =
            "Нужен адрес известной почты (gmail.com, mail.ru, yandex.ru, outlook.com и др.), например ivan@mail.ru.";

    /** Телефон: только цифры, от 10 до 15 штук (например, 89161234567). */
    private static final Pattern PHONE = Pattern.compile("\\d{10,15}");

    /** Номер рейса: код авиакомпании (2 латинские буквы или цифры) и 1–4 цифры, например SU1420. */
    private static final Pattern FLIGHT_NUMBER = Pattern.compile("[A-Z0-9]{2}\\d{1,4}");
    /** Код аэропорта IATA: 3 латинские буквы, например SVO. */
    private static final Pattern AIRPORT = Pattern.compile("[A-Z]{3}");

    /** Номера документов по типам. */
    private static final Pattern PASSPORT_RF = Pattern.compile("\\d{10}");
    private static final Pattern INTERNATIONAL_PASSPORT = Pattern.compile("\\d{9}");
    private static final Pattern BIRTH_CERTIFICATE = Pattern.compile("[IVXLC]{1,6}-[A-ZА-ЯЁ]{2}-\\d{6}");
    private static final Pattern FOREIGN_DOCUMENT = Pattern.compile("[A-Z0-9-]{5,20}");

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

    public static boolean isValidFlightNumber(String value) {
        return value != null && FLIGHT_NUMBER.matcher(value).matches();
    }

    public static boolean isValidAirportCode(String value) {
        return value != null && AIRPORT.matcher(value).matches();
    }

    public static boolean isValidDocumentNumber(DocumentType type, String value) {
        if (type == null || value == null) return false;
        Pattern pattern = switch (type) {
            case PASSPORT_RF -> PASSPORT_RF;
            case INTERNATIONAL_PASSPORT -> INTERNATIONAL_PASSPORT;
            case BIRTH_CERTIFICATE -> BIRTH_CERTIFICATE;
            case FOREIGN_DOCUMENT -> FOREIGN_DOCUMENT;
        };
        return pattern.matcher(value).matches();
    }

    /** Подсказка о формате номера документа — для сообщений об ошибке и полей ввода. */
    public static String documentNumberHint(DocumentType type) {
        if (type == null) return "";
        return switch (type) {
            case PASSPORT_RF -> "серия 4 цифры и номер 6 цифр, например 4510 123456";
            case INTERNATIONAL_PASSPORT -> "9 цифр, например 750123456";
            case BIRTH_CERTIFICATE -> "серия и номер, например VIII-МЮ-123456";
            case FOREIGN_DOCUMENT -> "5–20 латинских букв, цифр или дефисов";
        };
    }
}
