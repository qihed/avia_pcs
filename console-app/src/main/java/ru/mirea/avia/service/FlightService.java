package ru.mirea.avia.service;

import ru.mirea.avia.model.Flight;
import ru.mirea.avia.repository.FlightRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

public class FlightService {
    private final FlightRepository flightRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

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

    public Flight create(String flightNumber, String airline, String departureAirport, 
                   String arrivalAirport, LocalDateTime departureTime, LocalDateTime arrivalTime, 
                   String aircraftType, int totalSeats, BigDecimal baseFare) {
    try {
        // Создаем объект рейса через конструктор с параметрами
        Flight flight = new Flight(flightNumber, airline, departureAirport, arrivalAirport, 
                                   departureTime, arrivalTime, aircraftType, totalSeats, baseFare);

        // Сохраняем в базу данных (если ваш репозиторий использует .save() или .add())
        flightRepository.create(flight); 

        // Возвращаем созданный объект, чтобы ConsoleUI мог его принять
        return flight;
    } catch (SQLException e) {
        System.out.println("Ошибка при сохранении рейса: " + e.getMessage());
        return null;
    }
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

        // Вызываем ваш оригинальный метод создания без попытки присвоить результат в переменную
        create(flightNumber, airline, departureAirport, arrivalAirport, departureTime, arrivalTime, aircraftType, seatsCount, baseFare);
        System.out.println("Рейс успешно добавлен!");
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
