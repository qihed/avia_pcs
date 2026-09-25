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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Консольное меню системы бронирования авиабилетов. */
public class ConsoleUI {
    private static final List<FareClass> FARE_CLASSES = List.of(FareClass.values());
    private static final List<DocumentType> DOCUMENT_TYPES = List.of(DocumentType.values());
    private static final List<BookingStatus> STATUSES = List.of(BookingStatus.values());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

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
        // При старте просроченные брони аннулируются до показа меню.
        execute(() -> {
            int expired = bookings.expireOutdated();
            if (expired > 0) System.out.println("Аннулировано просроченных броней: " + expired);
        });
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
                    2. Список всех броней
                    3. Бронь по ID
                    4. Изменить бронь
                    5. Сменить статус
                    6. Отменить бронь
                    7. Удалить бронь
                    0. Назад
                    """);
            switch (input.integer("Выберите действие")) {
                case 1 -> execute(this::createBooking);
                case 2 -> execute(() -> printBookings(bookings.list()));
                case 3 -> execute(() -> printBookingCard(bookings.get(input.longNumber("ID бронирования"))));
                case 4 -> execute(this::updateBooking);
                case 5 -> execute(this::changeStatus);
                case 6 -> execute(() -> confirmAndCancel(input.longNumber("ID бронирования")));
                case 7 -> execute(this::deleteBooking);
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
        String lastName = input.required("Фамилия");
        String firstName = input.required("Имя");
        String middleName = input.text("Отчество (Enter - нет)");
        var birthDate = input.date("Дата рождения");
        DocumentType type = input.choice("Тип документа", DOCUMENT_TYPES, DocumentType::getTitle);
        String documentNumber = input.required("Номер документа");
        String email = input.required("E-mail");
        String phone = input.required("Телефон");
        Passenger passenger = passengers.create(lastName, firstName,
                middleName.isBlank() ? null : middleName, birthDate, type, documentNumber, email, phone);
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

    /** Изменение места, класса и стоимости; неизменяемая бронь отклоняется сразу после ввода ID. */
    private void updateBooking() throws SQLException {
        long id = input.longNumber("ID бронирования");
        Booking current = bookings.requireEditable(id);
        printBookingCard(current);
        String seatInput = input.text("Новый номер места (Enter — оставить " + current.getSeatNumber() + ")");
        // Место проверяется сразу (формат и занятость), чтобы не спрашивать класс и цену впустую.
        String seat = bookings.checkSeatChange(id, seatInput.isBlank() ? current.getSeatNumber() : seatInput);
        FareClass fareClass = input.choice("Новый класс обслуживания", FARE_CLASSES, FareClass::getTitle);
        BigDecimal price = input.decimal("Новая стоимость билета");
        Booking updated = bookings.update(id, seat, fareClass, price);
        System.out.println("Бронь изменена.");
        printBookingCard(updated);
    }

    /** Удаление с подтверждением; статус брони проверяется до вопроса «да/нет». */
    private void deleteBooking() throws SQLException {
        long id = input.longNumber("ID бронирования");
        Booking booking = bookings.requireDeletable(id);
        System.out.println(summary(booking) + ", статус: " + booking.getStatus().getTitle());
        if (!input.confirm("Удалить бронь без возможности восстановления?")) {
            System.out.println("Удаление отменено.");
            return;
        }
        bookings.delete(id);
        System.out.println("Бронирование удалено.");
    }

    /** Смена статуса: оператору предлагаются только переходы, допустимые из текущего статуса. */
    private void changeStatus() throws SQLException {
        long id = input.longNumber("ID бронирования");
        Booking booking = bookings.get(id);
        System.out.println(summary(booking) + ", текущий статус: " + booking.getStatus().getTitle());
        List<BookingStatus> targets = booking.getStatus().operatorTargets();
        if (targets.isEmpty()) {
            System.out.println("Бронь находится в финальном состоянии и не может быть изменена.");
            return;
        }
        System.out.println("Допустимые переходы:");
        BookingStatus target = input.choiceOrBack("Выберите новый статус", targets, BookingStatus::getTitle);
        if (target == null) return;
        if (target == BookingStatus.CANCELLED) {
            confirmAndCancel(id);
            return;
        }
        BookingStatus previous = booking.getStatus();
        Booking changed = bookings.changeStatus(id, target);
        System.out.println("Статус изменён: " + previous.getShortTitle() + " -> " + changed.getStatus().getShortTitle()
                + (target == BookingStatus.PAID ? ". Билет оформлен." : "."));
    }

    /** Отмена с подтверждением; статус и срок отмены проверяются до вопроса «да/нет». */
    private void confirmAndCancel(long id) throws SQLException {
        Booking booking = bookings.requireCancellable(id);
        System.out.println(summary(booking) + ", вылет " + formatDateTime(booking.getFlight().getDepartureTime()));
        if (!input.confirm("Отменить бронь? Место вернётся в продажу")) {
            System.out.println("Бронь не отменена.");
            return;
        }
        Booking cancelled = bookings.cancel(id);
        System.out.println("Бронь отменена. Место " + cancelled.getSeatNumber() + " на рейсе "
                + cancelled.getFlight().getFlightNumber() + " возвращено в продажу.");
    }

    private void showStatistics() throws SQLException {
        bookings.statistics().forEach((name, value) -> System.out.println(name + ": " + value));
    }

    private void exportExcel() throws SQLException, IOException {
        // Добавляем дату и время, чтобы новый экспорт не перезаписывал предыдущий файл.
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path filePath = Path.of("exports", "bookings_" + timestamp + ".xlsx");

        // Получаем бронирования из БД через сервис и передаём их ExcelExporter.
        Path path = exporter.export(bookings.list(), filePath);
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

    /** Список броней выровненной таблицей с заголовками столбцов. */
    private void printBookings(List<Booking> list) {
        if (list.isEmpty()) {
            System.out.println("Нет данных.");
            return;
        }
        String row = "%4s | %-16s | %-6s | %-10s | %-16s | %-5s | %-7s | %12s | %s%n";
        System.out.println();
        System.out.printf(row, "ID", "Пассажир", "Рейс", "Маршрут", "Вылет", "Место", "Класс", "Стоимость", "Статус");
        for (Booking booking : list) {
            Flight flight = booking.getFlight();
            System.out.printf(row, booking.getId(), booking.getPassenger().getShortName(), flight.getFlightNumber(),
                    flight.getRoute(), formatDateTime(flight.getDepartureTime()), booking.getSeatNumber(),
                    booking.getFareClass().getTitle(), formatMoney(booking.getPrice()), booking.getStatus().getTitle());
        }
        System.out.println("Всего броней: " + list.size());
    }

    /** Детальная карточка брони; полный номер документа выводится только здесь. */
    private void printBookingCard(Booking booking) {
        Passenger passenger = booking.getPassenger();
        Flight flight = booking.getFlight();
        System.out.println();
        System.out.println("   Бронирование      : № " + booking.getId());
        System.out.println("   Пассажир          : " + passenger.getFullName() + ", "
                + passenger.getBirthDate().format(DATE));
        System.out.println("   Документ          : " + passenger.getDocumentType().getTitle() + " "
                + passenger.getDocumentNumber());
        System.out.println("   Контакты          : " + passenger.getEmail() + ", " + passenger.getPhone());
        System.out.println("   Рейс              : " + flight.getFlightNumber() + " (" + flight.getAirline() + "), "
                + flight.getRoute());
        System.out.println("   Вылет / прилёт    : " + formatDateTime(flight.getDepartureTime()) + " / "
                + formatDateTime(flight.getArrivalTime()));
        System.out.println("   Воздушное судно   : " + flight.getAircraftType());
        System.out.println("   Место / класс     : " + booking.getSeatNumber() + ", " + booking.getFareClass().getTitle());
        System.out.println("   Стоимость         : " + formatMoney(booking.getPrice()) + " руб.");
        System.out.println("   Статус            : " + booking.getStatus().getTitle());
        System.out.println("   Создана           : " + formatDateTime(booking.getCreatedAt()));
    }

    /** Короткое описание брони для диалогов смены статуса, отмены и удаления. */
    private static String summary(Booking booking) {
        return "Бронь " + booking.getId() + ", " + booking.getPassenger().getShortName()
                + ", рейс " + booking.getFlight().getFlightNumber() + " " + booking.getFlight().getRoute();
    }

    private static String formatDateTime(LocalDateTime value) {
        return value.format(DATE_TIME);
    }

    /** Сумма с разделителем разрядов и двумя знаками после запятой: 12 740,00. */
    private static String formatMoney(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols).format(value);
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
        } catch (RuntimeException e) {
            // Любая непредвиденная ошибка возвращает оператора в меню, а не завершает программу.
            System.out.println("Непредвиденная ошибка: " + e);
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws SQLException, IOException;
    }
}
