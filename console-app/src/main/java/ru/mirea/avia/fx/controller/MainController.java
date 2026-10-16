package ru.mirea.avia.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import ru.mirea.avia.service.FlightService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Каркас главного окна: слева меню экранов, в центре текущий экран, снизу строка с подключением к БД.
 * Экраны добавляются через {@link #addScreen}; при каждом переходе экран перечитывает данные.
 * Пока нет экрана «Рейсы», его формы открываются из раздела «Формы» в меню.
 */
public final class MainController {
    private static final String ACTIVE = "nav-button";
    private static final String INACTIVE = "nav-link";

    @FXML private VBox navBox;
    @FXML private StackPane contentPane;
    @FXML private Label connectionBadge;
    @FXML private Label statusMessage;

    /** Кнопка меню → экран и действие при его показе. */
    private final Map<Button, Screen> screens = new LinkedHashMap<>();
    private FlightService flights;
    /** Что обновить после сохранения формы рейса (например, таблицу броней — в ней виден рейс). */
    private Runnable afterSave = () -> { };

    private record Screen(Parent view, Runnable onShow) { }

    /**
     * Добавляет экран в меню. Первый добавленный экран открывается сразу.
     * {@code onShow} вызывается при каждом переходе на экран — обычно это перечитывание данных из БД.
     */
    public void addScreen(String title, Parent view, Runnable onShow) {
        Button nav = new Button(title);
        nav.getStyleClass().add(INACTIVE);
        nav.setMaxWidth(Double.MAX_VALUE);
        nav.setOnAction(e -> select(nav));
        navBox.getChildren().add(nav);
        screens.put(nav, new Screen(view, onShow == null ? () -> { } : onShow));
        if (screens.size() == 1) {
            contentPane.getChildren().setAll(view);
            highlight(nav);
        }
    }

    public void setConnection(String connection) {
        connectionBadge.setText(connection);
    }

    private void select(Button nav) {
        Screen screen = screens.get(nav);
        contentPane.getChildren().setAll(screen.view());
        highlight(nav);
        statusMessage.setText("");
        screen.onShow().run();
    }

    private void highlight(Button active) {
        for (Button nav : screens.keySet()) {
            nav.getStyleClass().removeAll(ACTIVE, INACTIVE);
            nav.getStyleClass().add(nav == active ? ACTIVE : INACTIVE);
        }
    }

    /** Подключает формы рейса к пунктам меню «Формы». */
    public void setForms(FlightService flights, Runnable afterSave) {
        this.flights = flights;
        this.afterSave = afterSave == null ? () -> { } : afterSave;
    }

    // ---------- пункты меню «Формы» ----------

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
}
