package ru.mirea.avia.util;

import ru.mirea.avia.model.DocumentType;

import java.util.regex.Pattern;

/** Общие правила проверки полей для консоли, JavaFX-форм и сервисов. */
public final class Validators {
    /** Слово ФИО: первая буква заглавная, остальные строчные; двойные части через дефис. */
    private static final Pattern NAME = Pattern.compile("\\p{Lu}\\p{Ll}*(-\\p{Lu}\\p{Ll}*)*");
    /** E-mail: текст до @, домен и окончание .com или .ru. */
    private static final Pattern EMAIL =
            Pattern.compile("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)*\\.(com|ru)", Pattern.CASE_INSENSITIVE);

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
            case PASSPORT_RF -> "10 цифр без пробелов, например 4510123456";
            case INTERNATIONAL_PASSPORT -> "9 цифр, например 750123456";
            case BIRTH_CERTIFICATE -> "серия и номер, например VIII-МЮ-123456";
            case FOREIGN_DOCUMENT -> "5–20 латинских букв, цифр или дефисов";
        };
    }
}
