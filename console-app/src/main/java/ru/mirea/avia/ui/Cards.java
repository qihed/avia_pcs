package ru.mirea.avia.ui;

import ru.mirea.avia.dto.BookingDtos.BookingResponse;
import ru.mirea.avia.dto.BookingDtos.PriceQuote;
import ru.mirea.avia.dto.FlightDtos.FlightAvailability;
import ru.mirea.avia.dto.FlightDtos.FlightResponse;
import ru.mirea.avia.dto.PassengerDtos.PassengerResponse;
import ru.mirea.avia.util.DateTimeFormat;
import ru.mirea.avia.util.MoneyFormat;
import ru.mirea.avia.util.TextTable;
import ru.mirea.avia.util.UiText;

/**
 * Столбцы таблиц, карточки и краткие описания броней, рейсов и пассажиров.
 *
 * <p>Все разделы выводят сущности одинаково: даты — {@code дд.ММ.гггг ЧЧ:мм}, суммы —
 * с разделителем разрядов, статусы и классы — на русском (NFR-16). В списках номер
 * документа маскируется (NFR-09), а в детальной карточке выводится полностью.</p>
 */
final class Cards {
    private static final String NOTE_INDENT = "    ";

    private Cards() { }

    /** Возвращает таблицу броней для списков, поиска и фильтрации. */
    static TextTable<BookingResponse> bookingTable() {
        return TextTable.<BookingResponse>create()
                .rightColumn("ID", booking -> String.valueOf(booking.id()))
                .column("PNR", BookingResponse::bookingRef)
                .column("Пассажир", booking -> booking.passenger().shortName())
                .column("Рейс", booking -> booking.flight().flightNumber())
                .column("Маршрут", booking -> booking.flight().route())
                .column("Вылет", booking -> DateTimeFormat.format(booking.flight().departureTime()))
                .column("Место", BookingResponse::seatNumber)
                .column("Класс", booking -> UiText.fareClass(booking.fareClass()))
                .rightColumn("Стоимость", booking -> MoneyFormat.format(booking.price()))
                .column("Статус", booking -> UiText.statusShort(booking.status()));
    }

    /** Возвращает таблицу рейсов с загрузкой «свободно / всего». */
    static TextTable<FlightAvailability> flightTable() {
        return TextTable.<FlightAvailability>create()
                .rightColumn("ID", availability -> String.valueOf(availability.flight().id()))
                .column("Рейс", availability -> availability.flight().flightNumber())
                .column("Авиакомпания", availability -> availability.flight().airline())
                .column("Маршрут", availability -> availability.flight().route())
                .column("Вылет", availability -> DateTimeFormat.format(availability.flight().departureTime()))
                .column("Прилёт", availability -> DateTimeFormat.format(availability.flight().arrivalTime()))
                .column("Тип ВС", availability -> availability.flight().aircraftType())
                .rightColumn("Свободно", Cards::seats)
                .rightColumn("Тариф", availability -> MoneyFormat.format(availability.flight().basePrice()))
                .column("Статус", availability -> UiText.flightStatus(availability.flight().status()));
    }

    /** Возвращает сокращённую таблицу рейсов, открытых для продажи (диалог п. 10.3 ТЗ). */
    static TextTable<FlightAvailability> saleFlightTable() {
        return TextTable.<FlightAvailability>create()
                .rightColumn("ID", availability -> String.valueOf(availability.flight().id()))
                .column("Рейс", availability -> availability.flight().flightNumber())
                .column("Маршрут", availability -> availability.flight().route())
                .column("Вылет", availability -> DateTimeFormat.format(availability.flight().departureTime()))
                .column("Тип ВС", availability -> availability.flight().aircraftType())
                .rightColumn("Свободно", Cards::seats)
                .rightColumn("Тариф", availability -> MoneyFormat.format(availability.flight().basePrice()));
    }

    /** Возвращает таблицу пассажиров с маскированными номерами документов. */
    static TextTable<PassengerResponse> passengerTable() {
        return TextTable.<PassengerResponse>create()
                .rightColumn("ID", passenger -> String.valueOf(passenger.id()))
                .column("ФИО", PassengerResponse::fullName)
                .column("Дата рожд.", passenger -> DateTimeFormat.format(passenger.birthDate()))
                .column("Документ", Cards::document)
                .column("E-mail", PassengerResponse::email)
                .column("Телефон", PassengerResponse::phone);
    }

    /** Печатает детальную карточку брони (FR-07): номер документа выводится полностью. */
    static void booking(ConsoleIo io, BookingResponse booking) {
        PassengerResponse passenger = booking.passenger();
        FlightResponse flight = booking.flight();
        Ui.field(io, "Номер брони (PNR)", booking.bookingRef() + "   (ID " + booking.id() + ")");
        Ui.field(io, "Пассажир", passenger.fullName() + ", " + DateTimeFormat.format(passenger.birthDate()));
        Ui.field(io, "Документ", fullDocument(passenger));
        Ui.field(io, "Контакты", passenger.email() + ", " + passenger.phone());
        Ui.field(io, "Рейс", flightLine(flight));
        Ui.field(io, "Авиакомпания / ВС", flight.airline() + ", " + flight.aircraftType());
        Ui.field(io, "Место / класс", seatLine(booking));
        Ui.field(io, "Стоимость", MoneyFormat.formatRub(booking.price()));
        Ui.field(io, "Статус", UiText.status(booking.status()));
        Ui.field(io, "Создана / изменена", DateTimeFormat.format(booking.createdAt())
                + " / " + DateTimeFormat.format(booking.updatedAt()));
    }

    /** Печатает карточку пассажира вместе с числом его броней. */
    static void passenger(ConsoleIo io, PassengerResponse passenger, long bookings) {
        Ui.field(io, "ID", String.valueOf(passenger.id()));
        Ui.field(io, "ФИО", passenger.fullName());
        Ui.field(io, "Дата рождения", DateTimeFormat.format(passenger.birthDate())
                + " (" + UiText.years(passenger.age()) + ")");
        Ui.field(io, "Документ", fullDocument(passenger));
        Ui.field(io, "E-mail", passenger.email());
        Ui.field(io, "Телефон", passenger.phone());
        Ui.field(io, "Карточка создана", DateTimeFormat.format(passenger.createdAt()));
        Ui.field(io, "Бронирований", String.valueOf(bookings));
    }

    /** Печатает карточку рейса с загрузкой салона. */
    static void flight(ConsoleIo io, FlightAvailability availability) {
        FlightResponse flight = availability.flight();
        Ui.field(io, "ID", String.valueOf(flight.id()));
        Ui.field(io, "Рейс", flight.flightNumber() + ", " + flight.airline());
        Ui.field(io, "Маршрут", flight.route());
        Ui.field(io, "Вылет", DateTimeFormat.format(flight.departureTime()));
        Ui.field(io, "Прилёт", DateTimeFormat.format(flight.arrivalTime())
                + " (в пути " + UiText.duration(flight.duration()) + ")");
        Ui.field(io, "Воздушное судно", flight.aircraftType());
        Ui.field(io, "Места", "свободно " + availability.freeSeats() + " из " + flight.totalSeats()
                + " (занято " + availability.occupiedSeats() + ")");
        Ui.field(io, "Базовый тариф", MoneyFormat.formatRub(flight.basePrice()));
        Ui.field(io, "Статус", UiText.flightStatus(flight.status()));
    }

    /** Печатает строку результата операции с отступом карточки. */
    static void note(ConsoleIo io, String text) {
        io.println(NOTE_INDENT + text);
    }

    /**
     * Возвращает описание брони для подтверждений: «Бронь KJ90XZ, Новиков Д. В., рейс SU1420
     * SVO -> LED 17.09.2026 08:40, место 18A, текущий статус: Создано, ожидает оплаты».
     */
    static String bookingSummary(BookingResponse booking) {
        FlightResponse flight = booking.flight();
        return "Бронь " + booking.bookingRef() + ", " + booking.passenger().shortName()
                + ", рейс " + flight.flightNumber() + " " + flight.route()
                + " " + DateTimeFormat.format(flight.departureTime())
                + ", место " + booking.seatNumber()
                + ", текущий статус: " + UiText.status(booking.status());
    }

    /** Возвращает описание пассажира: «Иванов Иван Иванович, 12.04.1985, паспорт РФ 45 10 ****56». */
    static String passengerSummary(PassengerResponse passenger) {
        return passenger.fullName() + ", " + DateTimeFormat.format(passenger.birthDate()) + ", " + document(passenger);
    }

    /** Возвращает краткое описание рейса: «SU1420, SVO -> LED, 17.09.2026 08:40». */
    static String flightLine(FlightResponse flight) {
        return flight.flightNumber() + ", " + flight.route() + ", " + DateTimeFormat.format(flight.departureTime());
    }

    /** Возвращает место, класс и багаж брони: «12C, Комфорт, багаж включён». */
    static String seatLine(BookingResponse booking) {
        String baggage = booking.baggageIncluded() ? "багаж включён" : "без багажа";
        return booking.seatNumber() + ", " + UiText.fareClass(booking.fareClass()) + ", " + baggage;
    }

    /** Возвращает расчёт стоимости по BR-06: «6 400,00 x 1.6 + 2 500,00 = 12 740,00 руб.». */
    static String priceFormula(PriceQuote quote) {
        StringBuilder formula = new StringBuilder(MoneyFormat.format(quote.basePrice()))
                .append(" x ")
                .append(quote.coefficient().toPlainString());
        if (quote.baggageFee().signum() > 0) {
            formula.append(" + ").append(MoneyFormat.format(quote.baggageFee()));
        }
        formula.append(" = ").append(MoneyFormat.formatRub(quote.total()));
        // В бизнес-классе сбор не прибавляется, поэтому оператору поясняется, что багаж уже оплачен.
        if (quote.fareClass().isBaggageIncluded()) {
            formula.append(" (багаж входит в тариф)");
        }
        return formula.toString();
    }

    /** Возвращает тип и маскированный номер документа: «паспорт РФ 45 10 ****56». */
    static String document(PassengerResponse passenger) {
        return UiText.documentType(passenger.documentType()) + " " + passenger.maskedDocument();
    }

    private static String fullDocument(PassengerResponse passenger) {
        return UiText.documentType(passenger.documentType()) + " "
                + passenger.documentType().format(passenger.documentNumber());
    }

    private static String seats(FlightAvailability availability) {
        return availability.freeSeats() + "/" + availability.flight().totalSeats();
    }
}
