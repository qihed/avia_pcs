package ru.mirea.avia.ui;

import ru.mirea.avia.dto.FlightDtos.FlightRequest;
import ru.mirea.avia.dto.FlightDtos.FlightResponse;
import ru.mirea.avia.service.FlightService;
import ru.mirea.avia.service.FlightValidator;
import ru.mirea.avia.util.DateTimeFormat;
import ru.mirea.avia.util.MoneyFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Формирует запрос создания или изменения рейса (UC-09).
 *
 * <p>Поля проверяются сразу после ввода: маршрут, порядок вылета и прилёта, вылет нового
 * рейса в будущем и уникальность пары «номер + время вылета» (E-307). При ошибке
 * повторно запрашивается только неверное поле.</p>
 */
public final class FlightDialog {
    private static final String AIRLINE = "Авиакомпания";
    private static final String AIRCRAFT = "Тип воздушного судна";
    private static final int MAX_AIRLINE_LENGTH = 60;
    private static final int MAX_AIRCRAFT_LENGTH = 40;

    private FlightDialog() { }

    /** Запрашивает поля нового рейса и возвращает проверенный запрос. */
    public static FlightRequest create(ConsoleIo io, InputPrompt prompt, FlightService flights) {
        Ui.section(io, "НОВЫЙ РЕЙС");
        FlightValidator validator = flights.validator();
        String number = prompt.read("Номер рейса (например, SU1416)", validator::normalizeFlightNumber);
        String airline = prompt.read(AIRLINE, line -> validator.requireText(line, AIRLINE, MAX_AIRLINE_LENGTH));
        String from = prompt.read("Аэропорт вылета, код IATA (например, SVO)", validator::normalizeAirport);
        String to = prompt.read("Аэропорт назначения, код IATA", line -> checkArrivalAirport(validator, from, line));
        LocalDateTime departure = prompt.read("Вылет (" + DateTimeFormat.DATE_TIME_PATTERN + ")",
                line -> checkNewDeparture(flights, number, line));
        LocalDateTime arrival = readArrival(prompt, validator, departure);
        String aircraft = prompt.read(AIRCRAFT + " (например, Airbus A320)",
                line -> validator.requireText(line, AIRCRAFT, MAX_AIRCRAFT_LENGTH));
        int seats = prompt.read("Число мест (" + FlightValidator.MIN_SEATS + "–" + FlightValidator.MAX_SEATS + ")",
                line -> validator.checkSeats(InputPrompt.parseInt(line)));
        BigDecimal price = prompt.read("Базовый тариф эконом-класса, руб.",
                line -> validator.checkBasePrice(MoneyFormat.parse(line)));
        return new FlightRequest(number, airline, from, to, departure, arrival, aircraft, seats, price);
    }

    /** Запрашивает новые значения рейса; пустой ввод оставляет текущее значение поля. */
    public static FlightRequest edit(ConsoleIo io, InputPrompt prompt, FlightService flights,
                                     FlightResponse existing) {
        FlightValidator validator = flights.validator();
        Ui.info(io, "Рейс: " + Cards.flightLine(existing) + "   (Enter — оставить без изменений)");
        io.println();
        String number = prompt.readOrKeep("Номер рейса", existing.flightNumber(), existing.flightNumber(),
                validator::normalizeFlightNumber);
        String airline = prompt.readOrKeep(AIRLINE, existing.airline(), existing.airline(),
                line -> validator.requireText(line, AIRLINE, MAX_AIRLINE_LENGTH));
        String from = prompt.readOrKeep("Аэропорт вылета", existing.departureAirport(),
                existing.departureAirport(), validator::normalizeAirport);
        // Пустой ввод тоже проверяется: прежний пункт назначения может совпасть с новым пунктом вылета.
        String to = prompt.read("Аэропорт назначения [" + existing.arrivalAirport() + "]",
                line -> checkArrivalAirport(validator, from, line.isEmpty() ? existing.arrivalAirport() : line));
        LocalDateTime departure = prompt.read("Вылет [" + DateTimeFormat.format(existing.departureTime()) + "]",
                line -> checkChangedDeparture(flights, number, existing, line));
        LocalDateTime arrival = editArrival(prompt, validator, existing, departure);
        String aircraft = prompt.readOrKeep(AIRCRAFT, existing.aircraftType(), existing.aircraftType(),
                line -> validator.requireText(line, AIRCRAFT, MAX_AIRCRAFT_LENGTH));
        int seats = prompt.readOrKeep("Число мест", existing.totalSeats(), String.valueOf(existing.totalSeats()),
                line -> validator.checkSeats(InputPrompt.parseInt(line)));
        BigDecimal price = prompt.readOrKeep("Базовый тариф, руб.", existing.basePrice(),
                MoneyFormat.format(existing.basePrice()), line -> validator.checkBasePrice(MoneyFormat.parse(line)));
        return new FlightRequest(number, airline, from, to, departure, arrival, aircraft, seats, price);
    }

    private static LocalDateTime editArrival(InputPrompt prompt, FlightValidator validator,
                                             FlightResponse existing, LocalDateTime departure) {
        // Если новый вылет не раньше прежнего прилёта, прежнее время прилёта оставить нельзя.
        if (!existing.arrivalTime().isAfter(departure)) return readArrival(prompt, validator, departure);
        return prompt.readOrKeep("Прилёт", existing.arrivalTime(), DateTimeFormat.format(existing.arrivalTime()),
                line -> checkArrival(validator, departure, line));
    }

    private static LocalDateTime readArrival(InputPrompt prompt, FlightValidator validator,
                                             LocalDateTime departure) {
        return prompt.read("Прилёт (" + DateTimeFormat.DATE_TIME_PATTERN + ")",
                line -> checkArrival(validator, departure, line));
    }

    private static LocalDateTime checkArrival(FlightValidator validator, LocalDateTime departure, String line) {
        LocalDateTime arrival = DateTimeFormat.parseDateTime(line);
        validator.checkSchedule(departure, arrival);
        return arrival;
    }

    private static String checkArrivalAirport(FlightValidator validator, String from, String line) {
        String code = validator.normalizeAirport(line);
        validator.checkRoute(from, code);
        return code;
    }

    private static LocalDateTime checkNewDeparture(FlightService flights, String number, String line) {
        LocalDateTime departure = flights.checkFutureDeparture(DateTimeFormat.parseDateTime(line));
        flights.checkNumberFree(number, departure, null);
        return departure;
    }

    private static LocalDateTime checkChangedDeparture(FlightService flights, String number,
                                                       FlightResponse existing, String line) {
        LocalDateTime departure = line.isEmpty() ? existing.departureTime() : DateTimeFormat.parseDateTime(line);
        flights.checkNumberFree(number, departure, existing.id());
        return departure;
    }
}
