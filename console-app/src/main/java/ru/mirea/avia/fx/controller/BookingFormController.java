package ru.mirea.avia.fx.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import ru.mirea.avia.exception.BusinessException;
import ru.mirea.avia.exception.EntityNotFoundException;
import ru.mirea.avia.model.Booking;
import ru.mirea.avia.model.FareClass;
import ru.mirea.avia.model.Flight;
import ru.mirea.avia.model.Passenger;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.PassengerService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Форма бронирования (BookingFormDialog): добавление и изменение. Пассажир и рейс выбираются из списков,
 * а не вводятся по ID. Стоимость подставляется по тарифу рейса и классу, её можно поправить вручную.
 * Поля проверяются здесь, бизнес-правила (место занято, дубль брони, рейс закрыт) — в {@link BookingService},
 * его ошибки выводятся плашкой сверху.
 *
 * <pre>
 * BookingFormController.open(table.getScene().getWindow(), bookings, passengers, null)       // добавить
 *         .ifPresent(saved -> onRefresh());
 * BookingFormController.open(table.getScene().getWindow(), bookings, passengers, selected)   // изменить
 *         .ifPresent(saved -> onRefresh());
 * </pre>
 */
public final class BookingFormController implements FormDialogs.Form<Booking> {
    /** Ряд 1–99 без ведущего нуля (иначе 5A и 05A были бы разными местами) и буква A–F. */
    private static final Pattern SEAT = Pattern.compile("[1-9]\\d?[A-F]");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String NO_FLIGHT_HINT = "Выберите рейс, чтобы увидеть свободные места.";
    private static final String NO_TARIFF_HINT = "Подставится по тарифу рейса и классу; можно изменить.";

    @FXML private Label formError;
    @FXML private ComboBox<Passenger> passengerCombo;
    @FXML private Label passengerError;
    @FXML private ComboBox<Flight> flightCombo;
    @FXML private Label flightHint;
    @FXML private Label flightError;
    @FXML private TextField seatField;
    @FXML private Label seatError;
    @FXML private ComboBox<FareClass> fareClassCombo;
    @FXML private Label fareClassError;
    @FXML private TextField priceField;
    @FXML private Label priceHint;
    @FXML private Label priceError;

    private final BookingService bookings;
    private final PassengerService passengers;
    /** Редактируемая бронь; {@code null} — форма добавления. */
    private final Booking original;
    private FormErrors errors;
    private Booking saved;

    public BookingFormController(BookingService bookings, PassengerService passengers, Booking original) {
        this.bookings = bookings;
        this.passengers = passengers;
        this.original = original;
    }

    /**
     * Открывает модальную форму. {@code booking == null} — новая бронь, иначе изменение существующей
     * (пассажир и рейс у неё не меняются). Возвращает сохранённую бронь или пустой Optional при «Отмене».
     */
    public static Optional<Booking> open(Window owner, BookingService bookings, PassengerService passengers,
                                         Booking booking) {
        BookingFormController form = new BookingFormController(bookings, passengers, booking);
        String title = booking == null ? "Новое бронирование" : "Изменение брони";
        String header = booking == null ? "Новое бронирование"
                : "Бронь №" + booking.getId() + " · " + booking.getPassenger().getShortName();
        return FormDialogs.show(owner, "/fxml/BookingFormDialog.fxml", form, title, header);
    }

    @FXML
    private void initialize() {
        errors = new FormErrors(formError);
        errors.register(passengerCombo, passengerError, passengerCombo.valueProperty());
        errors.register(flightCombo, flightError, flightCombo.valueProperty());
        errors.register(seatField, seatError, seatField.textProperty());
        errors.register(fareClassCombo, fareClassError, fareClassCombo.valueProperty());
        errors.register(priceField, priceError, priceField.textProperty());

        // Ограничения ввода: лишнего символа просто не напечатать.
        FormFields.upperCase(seatField, "\\d{0,2}[A-F]?");
        FormFields.restrict(priceField, "\\d{0,8}([.,]\\d{0,2})?");

        passengerCombo.setConverter(FormFields.titles(BookingFormController::passengerTitle));
        flightCombo.setConverter(FormFields.titles(BookingFormController::flightTitle));
        fareClassCombo.getItems().setAll(FareClass.values());
        fareClassCombo.setConverter(FormFields.titles(FareClass::getTitle));
        // Подсказки всегда занимают место, поэтому высота формы не меняется при выборе рейса.
        flightHint.setText(NO_FLIGHT_HINT);
        priceHint.setText(NO_TARIFF_HINT);
        // Плашка ошибки может появиться уже после открытия окна — тогда окно подстраивает высоту.
        formError.textProperty().addListener(o -> fitWindow());

        if (original != null) fill(original);
        else loadChoices();

        // Слушатели ставятся после заполнения, чтобы при открытии изменения не перезаписать сохранённую цену.
        flightCombo.valueProperty().addListener((o, a, flight) -> {
            showFreeSeats(flight);
            recalculatePrice();
        });
        fareClassCombo.valueProperty().addListener((o, a, b) -> recalculatePrice());
    }

    /** Новая бронь: все пассажиры и только рейсы, открытые для продажи; в обоих списках работает поиск. */
    private void loadChoices() {
        List<Passenger> passengerList = List.of();
        List<Flight> flightList = List.of();
        try {
            passengerList = passengers.list().stream()
                    .sorted(Comparator.comparing(Passenger::getLastName).thenComparing(Passenger::getFirstName))
                    .toList();
            flightList = bookings.bookableFlights();
        } catch (SQLException e) {
            errors.showBanner("Не удалось загрузить пассажиров и рейсы: " + e.getMessage());
        }
        FormFields.searchable(passengerCombo, passengerList, BookingFormController::passengerTitle);
        FormFields.searchable(flightCombo, flightList, BookingFormController::flightTitle);
        if (flightList.isEmpty()) flightHint.setText("Нет рейсов, открытых для продажи.");
        fareClassCombo.setValue(FareClass.ECONOMY);
    }

    /** Изменение: текущие значения брони; пассажир и рейс показываются, но не меняются. */
    private void fill(Booking b) {
        passengerCombo.getItems().setAll(b.getPassenger());
        passengerCombo.setValue(b.getPassenger());
        passengerCombo.setDisable(true);
        flightCombo.getItems().setAll(b.getFlight());
        flightCombo.setValue(b.getFlight());
        flightCombo.setDisable(true);
        seatField.setText(b.getSeatNumber());
        fareClassCombo.setValue(b.getFareClass());
        priceField.setText(b.getPrice().toPlainString());
        showFreeSeats(b.getFlight());
        showTariff(b.getFlight(), b.getFareClass());
        errors.clearAll();
    }

    private void showFreeSeats(Flight flight) {
        if (flight == null) {
            flightHint.setText(NO_FLIGHT_HINT);
            return;
        }
        try {
            flightHint.setText("Свободно мест: " + bookings.freeSeats(flight) + " из " + flight.getTotalSeats());
        } catch (SQLException e) {
            flightHint.setText(NO_FLIGHT_HINT);
            errors.showBanner("Не удалось посчитать свободные места: " + e.getMessage());
        }
    }

    /** Подставляет стоимость по тарифу при смене рейса или класса. */
    private void recalculatePrice() {
        Flight flight = flightCombo.getValue();
        FareClass fareClass = fareClassCombo.getValue();
        if (flight == null || fareClass == null) {
            priceHint.setText(NO_TARIFF_HINT);
            return;
        }
        priceField.setText(bookings.suggestPrice(flight, fareClass).toPlainString());
        showTariff(flight, fareClass);
    }

    private void showTariff(Flight flight, FareClass fareClass) {
        priceHint.setText("По тарифу: " + flight.getBasePrice().toPlainString() + " × "
                + fareClass.getCoefficient().toPlainString() + " = "
                + bookings.suggestPrice(flight, fareClass).toPlainString() + " ₽; можно изменить");
    }

    @Override
    public boolean save() {
        errors.clearAll();
        Passenger passenger = passengerCombo.getValue();
        Flight flight = flightCombo.getValue();
        String seat = FormFields.text(seatField);
        FareClass fareClass = fareClassCombo.getValue();
        BigDecimal price = readPrice();

        if (passenger == null) errors.set(passengerCombo, "Выберите пассажира.");
        if (flight == null) errors.set(flightCombo, "Выберите рейс.");
        if (seat.isEmpty()) {
            errors.set(seatField, "Укажите место.");
        } else if (!SEAT.matcher(seat).matches()) {
            errors.set(seatField, "Ряд от 1 до 99 и буква A–F, например 12A.");
        }
        if (fareClass == null) errors.set(fareClassCombo, "Выберите класс обслуживания.");

        if (!errors.isEmpty()) {
            errors.focusFirstInvalid();
            return false;
        }

        try {
            saved = original == null
                    ? bookings.create(passenger.getId(), flight.getId(), seat, fareClass, price)
                    : bookings.update(original.getId(), seat, fareClass, price);
            return true;
        } catch (BusinessException | EntityNotFoundException e) {
            errors.showBanner(e.getMessage());
        } catch (SQLException e) {
            errors.showBanner("Ошибка базы данных: " + e.getMessage());
        } catch (RuntimeException e) {
            errors.showBanner("Непредвиденная ошибка: " + e.getMessage());
        }
        return false;
    }

    private BigDecimal readPrice() {
        String text = FormFields.text(priceField).replace(',', '.');
        if (text.isEmpty()) {
            errors.set(priceField, "Укажите стоимость.");
            return null;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(text);
        } catch (NumberFormatException e) {
            errors.set(priceField, "Число, например 6400 или 6400,50.");
            return null;
        }
        if (price.signum() <= 0) {
            errors.set(priceField, "Стоимость должна быть больше нуля.");
        } else if (price.compareTo(BookingService.MAX_PRICE) > 0) {
            errors.set(priceField, "Не больше " + BookingService.MAX_PRICE.toPlainString() + " ₽.");
        }
        return price;
    }

    /** Подпись пассажира в списке; по ней же идёт поиск (фамилия, имя, номер документа). */
    private static String passengerTitle(Passenger p) {
        return p.getFullName() + " · " + p.getDocumentType().getTitle() + " " + p.getDocumentNumber();
    }

    /** Подпись рейса в списке; по ней же идёт поиск (номер, аэропорты, дата, авиакомпания). */
    private static String flightTitle(Flight f) {
        return f.getFlightNumber() + " · " + f.getRoute() + " · " + DATE_TIME.format(f.getDepartureTime())
                + " · " + f.getAirline();
    }

    /** Подгоняет окно под содержимое, если оно уже показано. */
    private void fitWindow() {
        Platform.runLater(() -> {
            if (formError.getScene() != null && formError.getScene().getWindow() != null) {
                formError.getScene().getWindow().sizeToScene();
            }
        });
    }

    @Override
    public Booking result() {
        return saved;
    }

    @Override
    public Node initialFocus() {
        return original == null ? passengerCombo : seatField;
    }
}
