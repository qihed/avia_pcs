package ru.mirea.avia.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import ru.mirea.avia.exception.BusinessException;
import ru.mirea.avia.exception.EntityNotFoundException;
import ru.mirea.avia.model.Flight;
import ru.mirea.avia.model.FlightStatus;
import ru.mirea.avia.service.FlightService;
import ru.mirea.avia.util.Validators;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Форма рейса (FlightFormDialog): добавление и изменение. Новый рейс создаётся со статусом «По расписанию»,
 * статус меняется только при изменении. Проверки полей — здесь, бизнес-правила и сохранение — в {@link FlightService}.
 *
 * <pre>
 * // в экране «Рейсы»:
 * FlightFormController.open(table.getScene().getWindow(), flights, null)        // добавить
 *         .ifPresent(saved -> onRefresh());
 * FlightFormController.open(table.getScene().getWindow(), flights, selected)    // изменить
 *         .ifPresent(saved -> onRefresh());
 * </pre>
 */
public final class FlightFormController implements FormDialogs.Form<Flight> {
    @FXML private Label formError;
    @FXML private TextField flightNumberField;
    @FXML private Label flightNumberError;
    @FXML private TextField airlineField;
    @FXML private Label airlineError;
    @FXML private TextField departureAirportField;
    @FXML private Label departureAirportError;
    @FXML private TextField arrivalAirportField;
    @FXML private Label arrivalAirportError;
    @FXML private DatePicker departureDatePicker;
    @FXML private TextField departureTimeField;
    @FXML private Label departureError;
    @FXML private DatePicker arrivalDatePicker;
    @FXML private TextField arrivalTimeField;
    @FXML private Label arrivalError;
    @FXML private Label durationLabel;
    @FXML private TextField aircraftField;
    @FXML private Label aircraftError;
    @FXML private TextField seatsField;
    @FXML private Label seatsError;
    @FXML private TextField priceField;
    @FXML private Label priceError;
    @FXML private ComboBox<FlightStatus> statusCombo;
    @FXML private Label statusHint;
    @FXML private Label statusError;

    private final FlightService flights;
    /** Редактируемый рейс; {@code null} — форма добавления. */
    private final Flight original;
    private FormErrors errors;
    private Flight saved;

    public FlightFormController(FlightService flights, Flight original) {
        this.flights = flights;
        this.original = original;
    }

    /**
     * Открывает модальную форму. {@code flight == null} — новый рейс, иначе изменение существующего.
     * Возвращает сохранённый рейс или пустой Optional, если нажата «Отмена».
     */
    public static Optional<Flight> open(Window owner, FlightService flights, Flight flight) {
        FlightFormController form = new FlightFormController(flights, flight);
        String title = flight == null ? "Новый рейс" : "Изменение рейса";
        String header = flight == null ? "Новый рейс"
                : "Рейс " + flight.getFlightNumber() + " · " + flight.getDepartureAirport() + " → "
                + flight.getArrivalAirport();
        return FormDialogs.show(owner, "/fxml/FlightFormDialog.fxml", form, title, header);
    }

    @FXML
    private void initialize() {
        errors = new FormErrors(formError);
        errors.register(flightNumberField, flightNumberError, flightNumberField.textProperty());
        errors.register(airlineField, airlineError, airlineField.textProperty());
        errors.register(departureAirportField, departureAirportError, departureAirportField.textProperty());
        errors.register(arrivalAirportField, arrivalAirportError, arrivalAirportField.textProperty(),
                departureAirportField.textProperty());
        // Дата и время вылета (прилёта) делят одну строку ошибки: она привязана к полю даты.
        errors.register(departureDatePicker, departureError, departureDatePicker.valueProperty(),
                departureDatePicker.getEditor().textProperty(), departureTimeField.textProperty());
        errors.register(departureTimeField, new Label(), departureTimeField.textProperty(),
                departureDatePicker.valueProperty());
        errors.register(arrivalDatePicker, arrivalError, arrivalDatePicker.valueProperty(),
                arrivalDatePicker.getEditor().textProperty(), arrivalTimeField.textProperty(),
                departureDatePicker.valueProperty(), departureTimeField.textProperty());
        errors.register(arrivalTimeField, new Label(), arrivalTimeField.textProperty(),
                arrivalDatePicker.valueProperty());
        errors.register(aircraftField, aircraftError, aircraftField.textProperty());
        errors.register(seatsField, seatsError, seatsField.textProperty());
        errors.register(priceField, priceError, priceField.textProperty());
        errors.register(statusCombo, statusError, statusCombo.valueProperty());

        FormFields.upperCase(flightNumberField, "[A-Z0-9]{0,6}");
        FormFields.restrict(airlineField, ".{0," + FlightService.MAX_AIRLINE_LENGTH + "}");
        FormFields.upperCase(departureAirportField, "[A-Z]{0,3}");
        FormFields.upperCase(arrivalAirportField, "[A-Z]{0,3}");
        FormFields.restrict(departureTimeField, "\\d{0,2}(:\\d{0,2})?");
        FormFields.restrict(arrivalTimeField, "\\d{0,2}(:\\d{0,2})?");
        FormFields.restrict(aircraftField, ".{0," + FlightService.MAX_AIRCRAFT_LENGTH + "}");
        FormFields.digits(seatsField, 3);
        FormFields.restrict(priceField, "\\d{0,8}([.,]\\d{0,2})?");

        // Новый рейс нельзя поставить в прошлое; у существующего прошлые даты допустимы (рейс мог уже прилететь).
        FormFields.setupDatePicker(departureDatePicker,
                original == null ? date -> !date.isBefore(LocalDate.now()) : null);
        FormFields.setupDatePicker(arrivalDatePicker, null);
        // Дата прилёта по умолчанию совпадает с датой вылета.
        departureDatePicker.valueProperty().addListener((o, old, date) -> {
            if (date != null && (arrivalDatePicker.getValue() == null || arrivalDatePicker.getValue().equals(old))) {
                arrivalDatePicker.setValue(date);
            }
        });

        statusCombo.getItems().setAll(FlightStatus.values());
        statusCombo.setConverter(FormFields.titles(FlightStatus::getTitle));

        // Длительность пересчитывается при любом изменении времени вылета или прилёта.
        departureDatePicker.valueProperty().addListener((o, a, b) -> updateDuration());
        departureTimeField.textProperty().addListener((o, a, b) -> updateDuration());
        arrivalDatePicker.valueProperty().addListener((o, a, b) -> updateDuration());
        arrivalTimeField.textProperty().addListener((o, a, b) -> updateDuration());

        if (original != null) {
            fill(original);
        } else {
            statusCombo.setValue(FlightStatus.SCHEDULED);
            statusCombo.setDisable(true);
            statusHint.setText("Новый рейс создаётся со статусом «По расписанию».");
        }
        updateDuration();
    }

    private void fill(Flight f) {
        flightNumberField.setText(f.getFlightNumber());
        airlineField.setText(f.getAirline());
        departureAirportField.setText(f.getDepartureAirport());
        arrivalAirportField.setText(f.getArrivalAirport());
        departureDatePicker.setValue(f.getDepartureTime().toLocalDate());
        departureTimeField.setText(FormFields.format(f.getDepartureTime().toLocalTime()));
        arrivalDatePicker.setValue(f.getArrivalTime().toLocalDate());
        arrivalTimeField.setText(FormFields.format(f.getArrivalTime().toLocalTime()));
        aircraftField.setText(f.getAircraftType());
        seatsField.setText(String.valueOf(f.getTotalSeats()));
        priceField.setText(f.getBasePrice().stripTrailingZeros().toPlainString());
        statusCombo.setValue(f.getStatus());
        statusHint.setText("Брони оформляются, только пока рейс «По расписанию» или «Задержан».");
        errors.clearAll();
    }

    @Override
    public boolean save() {
        errors.clearAll();
        String number = FormFields.text(flightNumberField);
        String airline = FormFields.text(airlineField);
        String from = FormFields.text(departureAirportField);
        String to = FormFields.text(arrivalAirportField);
        LocalDateTime departure = readDateTime(departureDatePicker, departureTimeField, "вылета");
        LocalDateTime arrival = readDateTime(arrivalDatePicker, arrivalTimeField, "прилёта");
        String aircraft = FormFields.text(aircraftField);
        Integer seats = readSeats();
        BigDecimal price = readPrice();
        FlightStatus status = statusCombo.getValue();

        if (number.isEmpty()) {
            errors.set(flightNumberField, "Укажите номер рейса.");
        } else if (!Validators.isValidFlightNumber(number)) {
            errors.set(flightNumberField, "Код авиакомпании (2 символа) и 1–4 цифры, например SU1420.");
        }
        if (airline.isEmpty()) errors.set(airlineField, "Укажите авиакомпанию.");

        checkAirport(departureAirportField, from);
        checkAirport(arrivalAirportField, to);
        if (!from.isEmpty() && from.equals(to)) {
            errors.set(arrivalAirportField, "Совпадает с аэропортом вылета.");
        }

        if (departure != null && original == null && departure.isBefore(LocalDateTime.now())) {
            errors.set(departureDatePicker, "Вылет нового рейса не может быть в прошлом.");
        }
        if (departure != null && arrival != null) {
            if (!arrival.isAfter(departure)) {
                errors.set(arrivalDatePicker, "Прилёт должен быть позже вылета.");
            } else if (Duration.between(departure, arrival).toHours() >= FlightService.MAX_FLIGHT_HOURS) {
                errors.set(arrivalDatePicker, "Рейс не может длиться " + FlightService.MAX_FLIGHT_HOURS
                        + " часа и больше — проверьте даты.");
            }
        }

        if (aircraft.isEmpty()) errors.set(aircraftField, "Укажите тип воздушного судна.");
        if (status == null) errors.set(statusCombo, "Выберите статус.");

        if (!errors.isEmpty()) {
            errors.focusFirstInvalid();
            return false;
        }

        try {
            saved = original == null
                    ? flights.create(number, airline, from, to, departure, arrival, aircraft, seats, price)
                    : flights.update(original.getId(), number, airline, from, to, departure, arrival, aircraft,
                    seats, price, status);
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

    /** Дата + время из пары полей. Ошибка пишется в строку под датой; при ошибке возвращается null. */
    private LocalDateTime readDateTime(DatePicker datePicker, TextField timeField, String what) {
        LocalDate date;
        try {
            date = FormFields.date(datePicker);
        } catch (DateTimeParseException e) {
            errors.set(datePicker, "Дата " + what + " в формате дд.мм.гггг.");
            return null;
        }
        LocalTime time;
        try {
            time = FormFields.time(timeField);
        } catch (DateTimeParseException e) {
            markTime(datePicker, timeField, "Время " + what + " в формате ЧЧ:ММ, например 08:40.");
            return null;
        }
        if (date == null) {
            errors.set(datePicker, "Укажите дату " + what + ".");
            return null;
        }
        if (time == null) {
            markTime(datePicker, timeField, "Укажите время " + what + ".");
            return null;
        }
        return LocalDateTime.of(date, time);
    }

    private void markTime(DatePicker datePicker, TextField timeField, String message) {
        errors.set(datePicker, message);
        errors.set(timeField, " ");   // красная рамка у поля времени, текст ошибки — общий под датой
    }

    private Integer readSeats() {
        String text = FormFields.text(seatsField);
        if (text.isEmpty()) {
            errors.set(seatsField, "Укажите число мест.");
            return null;
        }
        int seats = Integer.parseInt(text);   // поле пропускает только цифры
        if (seats < 1 || seats > FlightService.MAX_SEATS) {
            errors.set(seatsField, "От 1 до " + FlightService.MAX_SEATS + ".");
            return null;
        }
        return seats;
    }

    private BigDecimal readPrice() {
        String text = FormFields.text(priceField).replace(',', '.');
        if (text.isEmpty()) {
            errors.set(priceField, "Укажите базовый тариф.");
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
            errors.set(priceField, "Тариф должен быть больше нуля.");
            return null;
        }
        return price;
    }

    private void checkAirport(TextField field, String code) {
        if (code.isEmpty()) {
            errors.set(field, "Укажите код аэропорта.");
        } else if (!Validators.isValidAirportCode(code)) {
            errors.set(field, "3 латинские буквы, например SVO.");
        }
    }

    /** «В пути: 1 ч 20 мин» под временем прилёта; пусто, пока время не задано полностью. */
    private void updateDuration() {
        String text = "";
        try {
            LocalDate depDate = departureDatePicker.getValue();
            LocalDate arrDate = arrivalDatePicker.getValue();
            LocalTime depTime = FormFields.time(departureTimeField);
            LocalTime arrTime = FormFields.time(arrivalTimeField);
            if (depDate != null && arrDate != null && depTime != null && arrTime != null) {
                Duration d = Duration.between(LocalDateTime.of(depDate, depTime), LocalDateTime.of(arrDate, arrTime));
                if (!d.isNegative() && !d.isZero()) {
                    text = "В пути: " + d.toHours() + " ч " + d.toMinutesPart() + " мин";
                }
            }
        } catch (DateTimeParseException e) {
            // время ещё не допечатано
        }
        durationLabel.setText(text);
    }

    @Override
    public Flight result() {
        return saved;
    }

    @Override
    public Node initialFocus() {
        return flightNumberField;
    }
}
