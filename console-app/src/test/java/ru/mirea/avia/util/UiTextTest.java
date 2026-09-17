package ru.mirea.avia.util;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.domain.FlightStatus;
import ru.mirea.avia.dto.ExportDtos.ExportFormat;
import ru.mirea.avia.dto.TableDtos.DatabaseTable;
import ru.mirea.avia.filter.BookingSort;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет русские подписи enum-кодов и согласование числительных в интерфейсе. */
class UiTextTest {
    @Test
    void localizesBookingStatuses() {
        assertThat(UiText.status(BookingStatus.CREATED)).isEqualTo("Создано, ожидает оплаты");
        assertThat(UiText.status(BookingStatus.PAID)).isEqualTo("Оплачено");
        assertThat(UiText.status(BookingStatus.CHECKED_IN)).isEqualTo("Регистрация пройдена");
        assertThat(UiText.status(BookingStatus.COMPLETED)).isEqualTo("Перелёт выполнен");
        assertThat(UiText.status(BookingStatus.CANCELLED)).isEqualTo("Отменено");
        assertThat(UiText.status(BookingStatus.EXPIRED)).isEqualTo("Бронь просрочена");
    }

    @Test
    void localizesShortBookingStatuses() {
        assertThat(UiText.statusShort(BookingStatus.CREATED)).isEqualTo("Создано");
        assertThat(UiText.statusShort(BookingStatus.PAID)).isEqualTo("Оплачено");
        assertThat(UiText.statusShort(BookingStatus.CHECKED_IN)).isEqualTo("Регистрация");
        assertThat(UiText.statusShort(BookingStatus.COMPLETED)).isEqualTo("Выполнено");
        assertThat(UiText.statusShort(BookingStatus.CANCELLED)).isEqualTo("Отменено");
        assertThat(UiText.statusShort(BookingStatus.EXPIRED)).isEqualTo("Просрочено");
    }

    @Test
    void statusLabelsMatchEnumTitles() {
        for (BookingStatus status : BookingStatus.values()) {
            assertThat(UiText.status(status)).isEqualTo(status.getTitle());
            assertThat(UiText.statusShort(status)).isEqualTo(status.getShortTitle());
        }
        for (FlightStatus status : FlightStatus.values()) {
            assertThat(UiText.flightStatus(status)).isEqualTo(status.getTitle());
        }
    }

    @Test
    void localizesFlightStatuses() {
        assertThat(UiText.flightStatus(FlightStatus.SCHEDULED)).isEqualTo("По расписанию");
        assertThat(UiText.flightStatus(FlightStatus.DELAYED)).isEqualTo("Задержан");
        assertThat(UiText.flightStatus(FlightStatus.DEPARTED)).isEqualTo("Вылетел");
        assertThat(UiText.flightStatus(FlightStatus.ARRIVED)).isEqualTo("Прибыл");
        assertThat(UiText.flightStatus(FlightStatus.CANCELLED)).isEqualTo("Отменён");
    }

    @Test
    void localizesFareClassesAndDocuments() {
        assertThat(UiText.fareClass(FareClass.ECONOMY)).isEqualTo("Эконом");
        assertThat(UiText.fareClass(FareClass.COMFORT)).isEqualTo("Комфорт");
        assertThat(UiText.fareClass(FareClass.BUSINESS)).isEqualTo("Бизнес");
        assertThat(UiText.documentType(DocumentType.PASSPORT_RF)).isEqualTo("паспорт РФ");
        assertThat(UiText.documentType(DocumentType.INTERNATIONAL_PASSPORT)).isEqualTo("загранпаспорт");
        assertThat(UiText.documentType(DocumentType.BIRTH_CERTIFICATE)).isEqualTo("свидетельство о рождении");
        assertThat(UiText.documentType(DocumentType.FOREIGN_DOCUMENT)).isEqualTo("иностранный документ");
    }

    @Test
    void localizesSorts() {
        assertThat(UiText.sort(BookingSort.BY_ID)).isEqualTo("по ID");
        assertThat(UiText.sort(BookingSort.BY_DEPARTURE)).isEqualTo("По дате и времени вылета (по возрастанию)");
        assertThat(UiText.sort(BookingSort.BY_PRICE_DESC)).isEqualTo("По стоимости (по убыванию)");
        assertThat(UiText.sort(BookingSort.BY_LAST_NAME)).isEqualTo("По фамилии пассажира (по алфавиту)");
        assertThat(UiText.sort(BookingSort.BY_CREATED_DESC)).isEqualTo("По дате создания брони (сначала новые)");
        assertThat(UiText.sort(BookingSort.BY_STATUS_THEN_DEPARTURE)).isEqualTo("По статусу, затем по дате вылета");
    }

    @Test
    void localizesExportFormatsTablesAndFlags() {
        assertThat(UiText.exportFormat(ExportFormat.XLSX)).isEqualTo("Excel (.xlsx)");
        assertThat(UiText.exportFormat(ExportFormat.CSV)).isEqualTo("CSV (.csv)");
        assertThat(UiText.table(DatabaseTable.PASSENGERS)).isEqualTo("passengers — Пассажиры");
        assertThat(UiText.table(DatabaseTable.FLIGHTS)).isEqualTo("flights — Рейсы");
        assertThat(UiText.table(DatabaseTable.BOOKINGS)).isEqualTo("bookings — Бронирования");
        assertThat(UiText.bool(true)).isEqualTo("Да");
        assertThat(UiText.bool(false)).isEqualTo("Нет");
    }

    @Test
    void returnsDashForNull() {
        assertThat(UiText.status(null)).isEqualTo("—");
        assertThat(UiText.statusShort(null)).isEqualTo("—");
        assertThat(UiText.flightStatus(null)).isEqualTo("—");
        assertThat(UiText.fareClass(null)).isEqualTo("—");
        assertThat(UiText.documentType(null)).isEqualTo("—");
        assertThat(UiText.exportFormat(null)).isEqualTo("—");
        assertThat(UiText.sort(null)).isEqualTo("—");
        assertThat(UiText.table(null)).isEqualTo("—");
        assertThat(UiText.duration(null)).isEqualTo("—");
    }

    @Test
    void replacesBlankValueWithDash() {
        assertThat(UiText.dash(null)).isEqualTo("—");
        assertThat(UiText.dash("   ")).isEqualTo("—");
        assertThat(UiText.dash("Иванович")).isEqualTo("Иванович");
    }

    @Test
    void pluralizesYears() {
        assertThat(UiText.years(0)).isEqualTo("0 лет");
        assertThat(UiText.years(1)).isEqualTo("1 год");
        assertThat(UiText.years(3)).isEqualTo("3 года");
        assertThat(UiText.years(5)).isEqualTo("5 лет");
        assertThat(UiText.years(11)).isEqualTo("11 лет");
        assertThat(UiText.years(14)).isEqualTo("14 лет");
        assertThat(UiText.years(21)).isEqualTo("21 год");
        assertThat(UiText.years(38)).isEqualTo("38 лет");
        assertThat(UiText.years(42)).isEqualTo("42 года");
        assertThat(UiText.years(111)).isEqualTo("111 лет");
    }

    @Test
    void pluralizesBookings() {
        assertThat(UiText.bookings(0)).isEqualTo("0 броней");
        assertThat(UiText.bookings(1)).isEqualTo("1 бронь");
        assertThat(UiText.bookings(2)).isEqualTo("2 брони");
        assertThat(UiText.bookings(4)).isEqualTo("4 брони");
        assertThat(UiText.bookings(5)).isEqualTo("5 броней");
        assertThat(UiText.bookings(12)).isEqualTo("12 броней");
        assertThat(UiText.bookings(21)).isEqualTo("21 бронь");
        assertThat(UiText.bookings(101)).isEqualTo("101 бронь");
    }

    @Test
    void formatsFlightDuration() {
        assertThat(UiText.duration(Duration.ofMinutes(80))).isEqualTo("1 ч 20 мин");
        assertThat(UiText.duration(Duration.ofMinutes(45))).isEqualTo("0 ч 45 мин");
        assertThat(UiText.duration(Duration.ofMinutes(150))).isEqualTo("2 ч 30 мин");
    }
}
