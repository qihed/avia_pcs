package ru.mirea.avia.service;

import ru.mirea.avia.exception.BusinessException;
import ru.mirea.avia.exception.EntityNotFoundException;
import ru.mirea.avia.model.Flight;
import ru.mirea.avia.model.FlightStatus;
import ru.mirea.avia.repository.FlightRepository;
import ru.mirea.avia.util.Validators;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class FlightService {
    private final FlightRepository flightRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static final int MAX_AIRLINE_LENGTH = 60;
    public static final int MAX_AIRCRAFT_LENGTH = 40;
    public static final int MAX_SEATS = 600;
    public static final int MAX_FLIGHT_HOURS = 24;
    /** NUMERIC(10,2) в таблице flights. */
    public static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    public FlightService(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    public List<Flight> list() {
        try {
            return flightRepository.findAll();
        } catch (SQLException e) {
            System.out.println("Ошибка при получении списка рейсов: " + e.getMessage());
            return List.of();
        }
    }

    public Flight get(long id) {
        try {
            return flightRepository.findById(id).orElse(null);
        } catch (SQLException e) {
            System.out.println("Ошибка при поиске рейса: " + e.getMessage());
            return null;
        }
    }

    /**
     * Создание рейса: проверка всех полей и бизнес-правил, затем сохранение. Новый рейс получает статус
     * «По расписанию». Нарушение правил — {@link BusinessException}, ошибка БД — {@link SQLException}.
     */
    public Flight create(String flightNumber, String airline, String departureAirport,
                         String arrivalAirport, LocalDateTime departureTime, LocalDateTime arrivalTime,
                         String aircraftType, int totalSeats, BigDecimal baseFare) throws SQLException {
        Flight flight = new Flight(normalizeCode(flightNumber), trim(airline), normalizeCode(departureAirport),
                normalizeCode(arrivalAirport), departureTime, arrivalTime, trim(aircraftType), totalSeats, baseFare);
        validate(flight);
        if (departureTime.isBefore(LocalDateTime.now())) {
            throw new BusinessException("Нельзя создать рейс с вылетом в прошлом.");
        }
        requireUniqueNumber(flight);
        return flightRepository.create(flight);
    }

    /** Изменение рейса (форма «Изменить»): все поля, включая статус. */
    public Flight update(long id, String flightNumber, String airline, String departureAirport,
                         String arrivalAirport, LocalDateTime departureTime, LocalDateTime arrivalTime,
                         String aircraftType, int totalSeats, BigDecimal baseFare, FlightStatus status)
            throws SQLException {
        flightRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Рейс с ID " + id + " не найден"));
        Flight flight = new Flight(id, normalizeCode(flightNumber), trim(airline), normalizeCode(departureAirport),
                normalizeCode(arrivalAirport), departureTime, arrivalTime, trim(aircraftType), totalSeats,
                baseFare, status);
        validate(flight);
        requireUniqueNumber(flight);
        int booked = flightRepository.countActiveBookings(id);
        if (totalSeats < booked) {
            throw new BusinessException("На рейс уже оформлено " + booked + " активных броней — мест не может быть меньше.");
        }
        return flightRepository.update(flight);
    }

    private void requireUniqueNumber(Flight flight) throws SQLException {
        if (flightRepository.existsByNumberAndDate(flight.getFlightNumber(),
                flight.getDepartureTime().toLocalDate(), flight.getId())) {
            throw new BusinessException("Рейс " + flight.getFlightNumber() + " на "
                    + flight.getDepartureTime().toLocalDate().format(DATE_ONLY) + " уже существует.");
        }
    }

    private void validate(Flight flight) {
        if (!Validators.isValidFlightNumber(flight.getFlightNumber())) {
            throw new BusinessException("Номер рейса: код авиакомпании из 2 символов и 1–4 цифры, например SU1420.");
        }
        if (flight.getAirline() == null || flight.getAirline().isEmpty()) {
            throw new BusinessException("Авиакомпания обязательна.");
        }
        if (flight.getAirline().length() > MAX_AIRLINE_LENGTH) {
            throw new BusinessException("Название авиакомпании не должно превышать " + MAX_AIRLINE_LENGTH + " символов.");
        }
        if (!Validators.isValidAirportCode(flight.getDepartureAirport())
                || !Validators.isValidAirportCode(flight.getArrivalAirport())) {
            throw new BusinessException("Код аэропорта IATA — 3 латинские буквы, например SVO.");
        }
        if (flight.getDepartureAirport().equals(flight.getArrivalAirport())) {
            throw new BusinessException("Аэропорты вылета и прилёта должны различаться.");
        }
        if (flight.getDepartureTime() == null || flight.getArrivalTime() == null) {
            throw new BusinessException("Укажите дату и время вылета и прилёта.");
        }
        if (!flight.getArrivalTime().isAfter(flight.getDepartureTime())) {
            throw new BusinessException("Время прилёта должно быть позже времени вылета.");
        }
        if (Duration.between(flight.getDepartureTime(), flight.getArrivalTime()).toHours() >= MAX_FLIGHT_HOURS) {
            throw new BusinessException("Рейс не может длиться больше " + MAX_FLIGHT_HOURS + " часов.");
        }
        if (flight.getAircraftType() == null || flight.getAircraftType().isEmpty()) {
            throw new BusinessException("Тип воздушного судна обязателен.");
        }
        if (flight.getAircraftType().length() > MAX_AIRCRAFT_LENGTH) {
            throw new BusinessException("Тип воздушного судна не должен превышать " + MAX_AIRCRAFT_LENGTH + " символов.");
        }
        if (flight.getTotalSeats() < 1 || flight.getTotalSeats() > MAX_SEATS) {
            throw new BusinessException("Число мест — от 1 до " + MAX_SEATS + ".");
        }
        if (flight.getBasePrice() == null || flight.getBasePrice().signum() <= 0) {
            throw new BusinessException("Базовый тариф должен быть больше нуля.");
        }
        if (flight.getBasePrice().scale() > 2 || flight.getBasePrice().compareTo(MAX_PRICE) > 0) {
            throw new BusinessException("Базовый тариф: не больше " + MAX_PRICE.toPlainString()
                    + " руб. и не больше двух знаков после запятой.");
        }
        if (flight.getStatus() == null) {
            throw new BusinessException("Статус рейса не выбран.");
        }
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public void addFlight(Scanner scanner) {
        System.out.println("\n--- Добавить рейс ---");

        String flightNumber = readNonEmptyString(scanner, "Номер рейса: ");
        String airline = readNonEmptyString(scanner, "Авиакомпания: ");
        String departureAirport = readAirportCode(scanner, "Аэропорт вылета (код IATA): ");
        String arrivalAirport = readAirportCode(scanner, "Аэропорт прилёта (код IATA): ");

        LocalDateTime departureTime = readDateTime(scanner, "Дата и время вылета (дд.ММ.гггг ЧЧ:мм): ");

        LocalDateTime arrivalTime;
        while (true) {
            arrivalTime = readDateTime(scanner, "Дата и время прилёта (дд.ММ.гггг ЧЧ:мм): ");
            if (arrivalTime.isAfter(departureTime)) {
                break;
            }
            System.out.println("Ошибка: Время прилёта должно быть позже времени вылета");
        }

        String aircraftType = readNonEmptyString(scanner, "Тип воздушного судна: ");
        int seatsCount = readPositiveInt(scanner, "Число мест: ");
        
        // Здесь тип цены подстраивается под ваш метод create (BigDecimal или double)
        double baseFareDouble = readPositiveDouble(scanner, "Базовый тариф: ");
        BigDecimal baseFare = BigDecimal.valueOf(baseFareDouble);

        try {
            create(flightNumber, airline, departureAirport, arrivalAirport, departureTime, arrivalTime,
                    aircraftType, seatsCount, baseFare);
            System.out.println("Рейс успешно добавлен!");
        } catch (BusinessException e) {
            System.out.println("Ошибка: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Ошибка при сохранении рейса: " + e.getMessage());
        }
    }

    private String readNonEmptyString(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("Ошибка: поле не должно быть пустым.");
        }
    }

    private String readAirportCode(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim().toUpperCase();
            if (input.matches("^[A-Z]{3}$")) {
                return input;
            }
            System.out.println("Ошибка: код IATA должен состоять из 3 латинских букв (например, SVO).");
        }
    }

    private LocalDateTime readDateTime(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return LocalDateTime.parse(input, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                System.out.println("Ошибка: неверный формат даты и времени.");
            }
        }
    }

    private int readPositiveInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                int value = Integer.parseInt(scanner.nextLine().trim());
                if (value > 0) {
                    return value;
                }
                System.out.println("Ошибка: число должно быть больше 0.");
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: введите целое число.");
            }
        }
    }

    private double readPositiveDouble(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                double value = Double.parseDouble(scanner.nextLine().trim().replace(',', '.'));
                if (value >= 0) {
                    return value;
                }
                System.out.println("Ошибка: значение не может быть отрицательным.");
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: введите число.");
            }
        }
    }
}
