package ru.mirea.avia.ui;

import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.dto.BookingDtos.BookingChangeRequest;
import ru.mirea.avia.dto.BookingDtos.BookingRequest;
import ru.mirea.avia.dto.BookingDtos.BookingResponse;
import ru.mirea.avia.dto.BookingDtos.PriceQuote;
import ru.mirea.avia.dto.FlightDtos.FlightAvailability;
import ru.mirea.avia.dto.FlightDtos.FlightResponse;
import ru.mirea.avia.dto.PassengerDtos.PassengerResponse;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.FareCalculator;
import ru.mirea.avia.service.FlightService;
import ru.mirea.avia.service.PassengerService;
import ru.mirea.avia.util.MoneyFormat;
import ru.mirea.avia.util.UiText;

import java.util.List;
import java.util.Optional;

/**
 * Формирует запросы оформления и изменения брони (UC-03, FR-08).
 *
 * <p>Каждый шаг диалога сразу проверяется сервисом, поэтому при ошибке повторяется только
 * текущий шаг, а не весь ввод. Стоимость показывается до подтверждения, а пустой результат
 * означает, что оператор отказался сохранять бронь.</p>
 */
public final class BookingDialog {
    private static final String NEW_PASSENGER = "+";
    private static final String PASSENGER_LIST = "?";
    private static final String PASSENGER_LABEL =
            "Введите ID пассажира (+ — новый пассажир, ? — список пассажиров)";
    private static final List<FareClass> FARE_CLASSES = List.of(FareClass.values());

    private BookingDialog() { }

    /** Проводит оператора по шагам оформления брони и возвращает подтверждённый запрос. */
    public static Optional<BookingRequest> create(ConsoleIo io, InputPrompt prompt, BookingService bookings,
                                                  PassengerService passengers, FlightService flights,
                                                  PassengersPane passengersPane) {
        Ui.section(io, "СОЗДАНИЕ БРОНИРОВАНИЯ");
        int expired = bookings.expireOutdated();
        if (expired > 0) Ui.info(io, "Аннулировано неоплаченных броней (BR-09): " + expired);
        PassengerResponse passenger = askPassenger(prompt, passengers, passengersPane);
        Cards.note(io, "Пассажир: " + Cards.passengerSummary(passenger));

        List<FlightAvailability> open = flights.listOpenForSale();
        if (open.isEmpty()) {
            Ui.info(io, "Нет рейсов, открытых для продажи. Бронирование невозможно");
            return Optional.empty();
        }
        printFlights(io, open);
        FlightResponse flight = prompt.read("Введите ID рейса",
                line -> selectFlight(line, passenger, bookings, flights));
        FareClass fareClass = prompt.readChoice("Класс обслуживания", FARE_CLASSES, UiText::fareClass);
        String seat = prompt.read("Номер места (например, 12A; ряды 1–" + flight.rowCount() + ")",
                line -> bookings.checkSeatAvailable(flight.id(), line));
        boolean baggage = askBaggage(io, prompt, fareClass);
        return confirm(io, prompt, bookings,
                new BookingRequest(passenger.id(), flight.id(), fareClass, seat, baggage));
    }

    /** Запрашивает новые класс, место и багаж брони; Enter оставляет текущее значение. */
    public static Optional<BookingChangeRequest> change(ConsoleIo io, InputPrompt prompt, BookingService bookings,
                                                        BookingResponse existing) {
        Cards.note(io, "Стоимость: " + MoneyFormat.formatRub(existing.price())
                + "   (Enter — оставить без изменений)");
        io.println();
        FareClass fareClass = prompt.readChoiceOrKeep("Класс обслуживания", FARE_CLASSES, UiText::fareClass,
                existing.fareClass());
        String seat = prompt.readOrKeep("Номер места", existing.seatNumber(), existing.seatNumber(),
                line -> checkNewSeat(line, bookings, existing));
        boolean baggage = askBaggageChange(prompt, fareClass, existing);
        if (!changed(existing, fareClass, seat, baggage)) {
            Ui.info(io, "Изменений нет, бронь не изменена");
            return Optional.empty();
        }

        PriceQuote quote = bookings.quote(existing.flight().id(), fareClass, baggage);
        io.println();
        Cards.note(io, "Новая стоимость: " + Cards.priceFormula(quote)
                + " (было " + MoneyFormat.formatRub(existing.price()) + ")");
        if (prompt.confirm("Сохранить изменения")) {
            return Optional.of(new BookingChangeRequest(seat, fareClass, baggage));
        }
        Ui.info(io, "Изменения не сохранены");
        return Optional.empty();
    }

    private static PassengerResponse askPassenger(InputPrompt prompt, PassengerService passengers,
                                                  PassengersPane passengersPane) {
        // Ввод «?» показывает список и повторяет запрос, «+» открывает карточку нового пассажира (UC-01).
        while (true) {
            PassengerInput input = prompt.read(PASSENGER_LABEL, line -> parsePassenger(line, passengers));
            if (input.passenger() != null) return input.passenger();
            if (NEW_PASSENGER.equals(input.command())) return passengersPane.createPassenger();
            passengersPane.printList();
        }
    }

    private static void printFlights(ConsoleIo io, List<FlightAvailability> open) {
        io.println();
        Ui.info(io, "Доступные рейсы:");
        Ui.table(io, Cards.saleFlightTable(), open);
        io.println();
    }

    private static boolean askBaggage(ConsoleIo io, InputPrompt prompt, FareClass fareClass) {
        if (fareClass.isBaggageIncluded()) {
            Ui.info(io, "Провоз багажа: включён в тариф «" + UiText.fareClass(fareClass) + "»");
            return true;
        }
        return prompt.confirm("Провоз багажа (+" + MoneyFormat.formatRub(FareCalculator.BAGGAGE_FEE) + ")");
    }

    private static boolean askBaggageChange(InputPrompt prompt, FareClass fareClass, BookingResponse existing) {
        if (fareClass.isBaggageIncluded()) return true;
        boolean current = existing.baggageIncluded();
        return prompt.readOrKeep("Провоз багажа (да/нет)", current, current ? "да" : "нет",
                InputPrompt::parseYesNo);
    }

    private static Optional<BookingRequest> confirm(ConsoleIo io, InputPrompt prompt, BookingService bookings,
                                                    BookingRequest request) {
        PriceQuote quote = bookings.quote(request.flightId(), request.fareClass(), request.baggageIncluded());
        io.println();
        Cards.note(io, "Расчёт стоимости: " + Cards.priceFormula(quote));
        if (prompt.confirm("Подтвердить бронирование")) return Optional.of(request);
        Ui.info(io, "Бронирование не создано");
        return Optional.empty();
    }

    private static PassengerInput parsePassenger(String line, PassengerService passengers) {
        return switch (line) {
            case NEW_PASSENGER, PASSENGER_LIST -> new PassengerInput(line, null);
            default -> new PassengerInput(null, passengers.get(InputPrompt.parseId(line)));
        };
    }

    private static FlightResponse selectFlight(String line, PassengerResponse passenger, BookingService bookings,
                                               FlightService flights) {
        long flightId = InputPrompt.parseId(line);
        // BR-01, BR-02 и BR-04 проверяются сразу, чтобы оператор выбрал другой рейс, не вводя место.
        bookings.checkFlightAvailable(passenger.id(), flightId);
        return flights.get(flightId).flight();
    }

    private static String checkNewSeat(String line, BookingService bookings, BookingResponse existing) {
        String seat = bookings.normalizeSeat(line);
        // Своё текущее место бронь уже занимает, поэтому проверка занятости для него не нужна.
        if (seat.equals(existing.seatNumber())) return seat;
        return bookings.checkSeatAvailable(existing.flight().id(), seat);
    }

    private static boolean changed(BookingResponse existing, FareClass fareClass, String seat, boolean baggage) {
        return fareClass != existing.fareClass()
                || !seat.equals(existing.seatNumber())
                || baggage != existing.baggageIncluded();
    }

    /** Ответ на запрос пассажира: найденная карточка либо команда «+» или «?». */
    private record PassengerInput(String command, PassengerResponse passenger) {
    }
}
