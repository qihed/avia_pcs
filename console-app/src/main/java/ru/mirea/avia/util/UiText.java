package ru.mirea.avia.util;

import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.domain.FlightStatus;
import ru.mirea.avia.dto.ExportDtos.ExportFormat;
import ru.mirea.avia.dto.TableDtos.DatabaseTable;
import ru.mirea.avia.filter.BookingSort;

import java.time.Duration;

/**
 * Единая локализация enum-кодов и числительных для консольного интерфейса.
 *
 * <p>Коды перечислений остаются английскими и хранятся в БД без изменений, а русские
 * подписи задаются только на уровне интерфейса и выгрузки.</p>
 */
public final class UiText {
    private UiText() { }

    public static String status(BookingStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case CREATED -> "Создано, ожидает оплаты";
            case PAID -> "Оплачено";
            case CHECKED_IN -> "Регистрация пройдена";
            case COMPLETED -> "Перелёт выполнен";
            case CANCELLED -> "Отменено";
            case EXPIRED -> "Бронь просрочена";
        };
    }

    /** Возвращает краткое название статуса брони для столбца таблицы. */
    public static String statusShort(BookingStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case CREATED -> "Создано";
            case PAID -> "Оплачено";
            case CHECKED_IN -> "Регистрация";
            case COMPLETED -> "Выполнено";
            case CANCELLED -> "Отменено";
            case EXPIRED -> "Просрочено";
        };
    }

    public static String flightStatus(FlightStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case SCHEDULED -> "По расписанию";
            case DELAYED -> "Задержан";
            case DEPARTED -> "Вылетел";
            case ARRIVED -> "Прибыл";
            case CANCELLED -> "Отменён";
        };
    }

    public static String fareClass(FareClass fareClass) {
        if (fareClass == null) return "—";
        return switch (fareClass) {
            case ECONOMY -> "Эконом";
            case COMFORT -> "Комфорт";
            case BUSINESS -> "Бизнес";
        };
    }

    /** Возвращает название документа со строчной буквы, чтобы его можно было вставить в середину фразы. */
    public static String documentType(DocumentType type) {
        if (type == null) return "—";
        return switch (type) {
            case PASSPORT_RF -> "паспорт РФ";
            case INTERNATIONAL_PASSPORT -> "загранпаспорт";
            case BIRTH_CERTIFICATE -> "свидетельство о рождении";
            case FOREIGN_DOCUMENT -> "иностранный документ";
        };
    }

    public static String bool(boolean value) {
        return value ? "Да" : "Нет";
    }

    public static String exportFormat(ExportFormat format) {
        if (format == null) return "—";
        return switch (format) {
            case XLSX -> "Excel (.xlsx)";
            case CSV -> "CSV (.csv)";
        };
    }

    public static String sort(BookingSort sort) {
        if (sort == null) return "—";
        return switch (sort) {
            case BY_ID -> "по ID";
            case BY_DEPARTURE -> "По дате и времени вылета (по возрастанию)";
            case BY_PRICE_DESC -> "По стоимости (по убыванию)";
            case BY_LAST_NAME -> "По фамилии пассажира (по алфавиту)";
            case BY_CREATED_DESC -> "По дате создания брони (сначала новые)";
            case BY_STATUS_THEN_DEPARTURE -> "По статусу, затем по дате вылета";
        };
    }

    /** Возвращает имя таблицы БД вместе с русским названием: {@code passengers — Пассажиры}. */
    public static String table(DatabaseTable table) {
        if (table == null) return "—";
        return switch (table) {
            case PASSENGERS -> "passengers — Пассажиры";
            case FLIGHTS -> "flights — Рейсы";
            case BOOKINGS -> "bookings — Бронирования";
        };
    }

    /** Возвращает возраст с согласованным словом: «1 год», «3 года», «38 лет». */
    public static String years(int count) {
        return plural(count, "год", "года", "лет");
    }

    /** Возвращает число броней с согласованным словом: «1 бронь», «2 брони», «5 броней». */
    public static String bookings(long count) {
        return plural(count, "бронь", "брони", "броней");
    }

    /** Возвращает длительность полёта в виде «1 ч 20 мин». */
    public static String duration(Duration duration) {
        if (duration == null) return "—";
        return duration.toHours() + " ч " + duration.toMinutesPart() + " мин";
    }

    /** Возвращает значение либо прочерк, если оно не заполнено. */
    public static String dash(String value) {
        if (value == null || value.isBlank()) return "—";
        return value;
    }

    /** «1 запись», «2 записи», «25 записей» — подпись счётчика строк. */
    public static String records(long count) {
        return plural(count, "запись", "записи", "записей");
    }

    private static String plural(long count, String one, String few, String many) {
        long mod10 = count % 10;
        long mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) return count + " " + one;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return count + " " + few;
        return count + " " + many;
    }
}
