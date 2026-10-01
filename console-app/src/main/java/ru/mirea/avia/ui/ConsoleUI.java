package ru.mirea.avia.ui;

import ru.mirea.avia.exception.BusinessException;
import ru.mirea.avia.exception.EntityNotFoundException;
import ru.mirea.avia.model.Booking;
import ru.mirea.avia.model.BookingStatus;
import ru.mirea.avia.model.DocumentType;
import ru.mirea.avia.model.FareClass;
import ru.mirea.avia.model.Flight;
import ru.mirea.avia.model.Passenger;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.FlightService;
import ru.mirea.avia.service.PassengerService;
import ru.mirea.avia.util.ExcelExporter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

/** Консольное меню системы бронирования авиабилетов. */
public class ConsoleUI {
    private static final List<FareClass> FARE_CLASSES = List.of(FareClass.values());
    private static final List<DocumentType> DOCUMENT_TYPES = List.of(DocumentType.values());
    private static final List<BookingStatus> STATUSES = List.of(BookingStatus.values());

    private final PassengerService passengers;
    private final FlightService flights;
    private final BookingService bookings;
    private final ExcelExporter exporter;
    private final Input input = new Input();

    public ConsoleUI(PassengerService passengers, FlightService flights, BookingService bookings,
                     ExcelExporter exporter) {
        this.passengers = passengers;
        this.flights = flights;
        this.bookings = bookings;
        this.exporter = exporter;
    }

    public void run() {
        while (true) {
            System.out.println("""
                    ========================================
                       СИСТЕМА БРОНИРОВАНИЯ АВИАБИЛЕТОВ
                    ========================================
                    1. Пассажиры
                    2. Рейсы
                    3. Бронирования
                    4. Поиск
                    5. Фильтрация
                    6. Сортировка
                    7. Статистика
                    8. Экспорт в Excel
                    9. Вывести таблицы базы данных
                    0. Выход
                    """);
            switch (input.integer("Выберите действие")) {
                case 1 -> passengersMenu();
                case 2 -> flightsMenu();
                case 3 -> bookingsMenu();
                case 4 -> searchMenu();
                case 5 -> filterMenu();
                case 6 -> sortMenu();
                case 7 -> execute(this::showStatistics);
                case 8 -> execute(this::exportExcel);
                case 9 -> execute(this::showDatabaseTables);
                case 0 -> { return; }
                default -> System.out.println("Нет такого пункта меню.");
            }
        }
    }

    private void passengersMenu() {
        while (true) {
            System.out.println("\n1. Добавить пассажира\n2. Показать пассажиров\n3. Найти по ID\n0. Назад");
            switch (input.integer("Выберите действие")) {
                case 1 -> execute(this::createPassenger);
                case 2 -> execute(() -> printAll(passengers.list()));
                case 3 -> execute(() -> System.out.println(passengers.get(input.longNumber("ID"))));
                case 0 -> { return; }
                default -> System.out.println("Нет такого пункта меню.");
            }
        }
    }

    private void flightsMenu() {
        while (true) {
            System.out.println("\n1. Добавить рейс\n2. Показать рейсы\n3. Найти по ID\n0. Назад");
            switch (input.integer("Выберите действие")) {
                case 1 -> execute(this::createFlight);
                case 2 -> execute(() -> printAll(flights.list()));
                case 3 -> execute(() -> System.out.println(flights.get(input.longNumber("ID"))));
                case 0 -> { return; }
                default -> System.out.println("Нет такого пункта меню.");
            }
        }
    }

    private void bookingsMenu() {
        while (true) {
            System.out.println("""

                    1. Создать бронирование
                    2. Показать все
                    3. Получить по ID
                    4. Изменить
                    5. Удалить
                    6. Изменить статус
                    0. Назад
                    """);
            switch (input.integer("Выберите действие")) {
                case 1 -> execute(this::createBooking);
                case 2 -> execute(() -> printAll(bookings.list()));
                case 3 -> execute(() -> System.out.println(bookings.get(input.longNumber("ID"))));
                case 4 -> execute(this::updateBooking);
                case 5 -> execute(this::deleteBooking);
                case 6 -> execute(this::changeStatus);
                case 0 -> { return; }
                default -> System.out.println("Нет такого пункта меню.");
            }
        }
    }

    private void searchMenu() {
        System.out.println("\n1. По фамилии пассажира\n2. По номеру рейса");
        execute(() -> {
            List<Booking> result = switch (input.integer("Способ поиска")) {
                case 1 -> bookings.searchByPassengerLastName(input.required("Фамилия или её часть"));
                case 2 -> bookings.searchByFlightNumber(input.required("Номер или часть номера рейса"));
                default -> throw new BusinessException("Нет такого способа поиска");
            };
            printAll(result);
        });
    }

    private void filterMenu() {
        System.out.println("\n1. По статусу\n2. По классу обслуживания");
        execute(() -> {
            List<Booking> result = switch (input.integer("Фильтр")) {
                case 1 -> bookings.filterByStatus(input.choice("Статус", STATUSES, BookingStatus::getTitle));
                case 2 -> bookings.filterByFareClass(input.choice("Класс", FARE_CLASSES, FareClass::getTitle));
                default -> throw new BusinessException("Нет такого фильтра");
            };
            printAll(result);
        });
    }

    private void sortMenu() {
        System.out.println("\n1. По цене\n2. По дате вылета");
        execute(() -> {
            List<Booking> result = switch (input.integer("Сортировка")) {
                case 1 -> bookings.sortByPrice();
                case 2 -> bookings.sortByDepartureTime();
                default -> throw new BusinessException("Нет такого способа сортировки");
            };
            printAll(result);
        });
    }

    private void createPassenger() throws SQLException {
        String lastName = input.maxLength("Фамилия", 60, true);
        String firstName = input.maxLength("Имя", 60, true);
        String middleName = input.maxLength("Отчество (Enter - нет)", 60, false);

        var birthDate = input.date("Дата рождения");
        DocumentType type = input.choice("Тип документа", DOCUMENT_TYPES, DocumentType::getTitle);
        String documentNumber = input.required("Номер документа");
        String email = input.required("E-mail");
        String phone = input.required("Телефон");

        Passenger passenger = passengers.create(
                lastName,
                firstName,
                middleName.isBlank() ? null : middleName,
                birthDate,
                type,
                documentNumber,
                email,
                phone
        );

        System.out.println("Создан пассажир: " + passenger);
    }

    private void createFlight() throws SQLException {
        String flightNumber = input.required("Номер рейса").toUpperCase();
        String airline = input.required("Авиакомпания");
        String from = input.required("Аэропорт вылета (код IATA)").toUpperCase();
        String to = input.required("Аэропорт прилёта (код IATA)").toUpperCase();
        var departure = input.dateTime("Дата и время вылета");
        var arrival = input.dateTime("Дата и время прилёта");
        String aircraft = input.required("Тип воздушного судна");
        int seats = input.integer("Число мест");
        BigDecimal price = input.decimal("Базовый тариф");
        Flight flight = flights.create(flightNumber, airline, from, to, departure, arrival, aircraft, seats, price);
        System.out.println("Создан рейс: " + flight);
    }

    private void createBooking() throws SQLException {
        long passengerId = input.longNumber("ID пассажира");
        long flightId = input.longNumber("ID рейса");
        String seat = input.required("Номер места (например, 12A)");
        FareClass fareClass = input.choice("Класс обслуживания", FARE_CLASSES, FareClass::getTitle);
        BigDecimal price = input.decimal("Стоимость билета");
        Booking booking = bookings.create(passengerId, flightId, seat, fareClass, price);
        System.out.println("Создано: " + booking);
    }

    private void updateBooking() throws SQLException {
        long id = input.longNumber("ID бронирования");
        Booking current = bookings.get(id);
        System.out.println("Текущая бронь: " + current);
        String seat = input.required("Новый номер места");
        FareClass fareClass = input.choice("Новый класс обслуживания", FARE_CLASSES, FareClass::getTitle);
        BigDecimal price = input.decimal("Новая стоимость билета");
        System.out.println("Изменено: " + bookings.update(id, seat, fareClass, price));
    }

    private void deleteBooking() throws SQLException {
        long id = input.longNumber("ID бронирования");
        bookings.delete(id);
        System.out.println("Бронирование удалено.");
    }

    private void changeStatus() throws SQLException {
        long id = input.longNumber("ID бронирования");
        BookingStatus status = input.choice("Новый статус", STATUSES, BookingStatus::getTitle);
        System.out.println("Новый статус: " + bookings.changeStatus(id, status));
    }

    private void showStatistics() throws SQLException {
        bookings.statistics().forEach((name, value) -> System.out.println(name + ": " + value));
    }

    private void exportExcel() throws SQLException, IOException {
        Path path = exporter.export(bookings.list(), Path.of("exports", "bookings.xlsx"));
        System.out.println("Файл создан: " + path.toAbsolutePath());
    }

    private void showDatabaseTables() throws SQLException {
        System.out.println("\n--- passengers ---");
        printAll(passengers.list());
        System.out.println("\n--- flights ---");
        printAll(flights.list());
        System.out.println("\n--- bookings ---");
        printAll(bookings.list());
    }

    private void printAll(List<?> list) {
        if (list.isEmpty()) System.out.println("Нет данных.");
        else list.forEach(System.out::println);
    }

    private void execute(Action action) {
        try {
            action.run();
        } catch (BusinessException | EntityNotFoundException e) {
            System.out.println("Ошибка: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Ошибка базы данных: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Ошибка экспорта: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws SQLException, IOException;
    }
}
