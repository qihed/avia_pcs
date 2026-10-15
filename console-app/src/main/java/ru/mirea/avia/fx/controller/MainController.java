package ru.mirea.avia.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import ru.mirea.avia.model.Flight;
import ru.mirea.avia.model.Passenger;
import ru.mirea.avia.service.FlightService;
import ru.mirea.avia.service.PassengerService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Каркас главного окна: слева меню, в центре текущий экран, снизу строка с подключением к БД.
 * Пока нет экранов «Пассажиры» и «Рейсы», их формы открываются из раздела «Формы» в меню.
 */
public final class MainController {
    @FXML private StackPane contentPane;
    @FXML private Label connectionBadge;
    @FXML private Label statusMessage;

    private PassengerService passengers;
    private FlightService flights;
    /** Что обновить после сохранения формы (например, таблицу броней — в ней видны пассажир и рейс). */
    private Runnable afterSave = () -> { };

    public void show(Parent view, String connection) {
        contentPane.getChildren().setAll(view);
        connectionBadge.setText(connection);
    }

    /** Подключает формы пассажира и рейса к пунктам меню. */
    public void setForms(PassengerService passengers, FlightService flights, Runnable afterSave) {
        this.passengers = passengers;
        this.flights = flights;
        this.afterSave = afterSave == null ? () -> { } : afterSave;
    }

    // ---------- пункты меню «Формы» ----------

    @FXML
    private void onNewPassenger() {
        PassengerFormController.open(window(), passengers, null)
                .ifPresent(p -> saved("Добавлен пассажир №" + p.getId() + ": " + p.getFullName()));
    }

    @FXML
    private void onEditPassenger() {
        try {
            choose("Изменить пассажира", "Пассажир:", passengers.list(),
                    p -> "№" + p.getId() + " · " + p.getFullName())
                    .flatMap(p -> PassengerFormController.open(window(), passengers, p))
                    .ifPresent(p -> saved("Изменён пассажир №" + p.getId() + ": " + p.getFullName()));
        } catch (Exception e) {
            error(e);
        }
    }

    @FXML
    private void onNewFlight() {
        FlightFormController.open(window(), flights, null)
                .ifPresent(f -> saved("Добавлен рейс " + f.getFlightNumber() + " · " + f.getRoute()));
    }

    @FXML
    private void onEditFlight() {
        choose("Изменить рейс", "Рейс:", flights.list(),
                f -> "№" + f.getId() + " · " + f.getFlightNumber() + " · " + f.getRoute() + " · "
                        + FormFields.DATE.format(f.getDepartureTime()))
                .flatMap(f -> FlightFormController.open(window(), flights, f))
                .ifPresent(f -> saved("Изменён рейс " + f.getFlightNumber() + " · " + f.getRoute()));
    }

    /** Выбор записи для изменения из списка (стандартный ChoiceDialog). */
    private <T> Optional<T> choose(String title, String caption, List<T> items, Function<T, String> label) {
        if (items.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "Список пуст — сначала добавьте запись.").showAndWait();
            return Optional.empty();
        }
        Map<String, T> byLabel = new LinkedHashMap<>();
        items.forEach(item -> byLabel.put(label.apply(item), item));
        ChoiceDialog<String> dialog = new ChoiceDialog<>(byLabel.keySet().iterator().next(), byLabel.keySet());
        dialog.initOwner(window());
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setContentText(caption);
        return dialog.showAndWait().map(byLabel::get);
    }

    private void saved(String message) {
        statusMessage.setText(message);
        afterSave.run();
    }

    private Window window() {
        return contentPane.getScene().getWindow();
    }

    private void error(Exception e) {
        new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
    }
}
