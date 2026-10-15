package ru.mirea.avia.fx.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Optional;

/**
 * Общая обвязка модальных форм (пассажир, рейс, а дальше и бронь): FXML с корнем {@code DialogPane},
 * кнопки «Сохранить» и «Отмена», общие стили. Окно не закрывается, пока форма не сохранится без ошибок.
 */
public final class FormDialogs {

    /** Контроллер формы. Как и у экранов, сервисы передаются ему через конструктор. */
    public interface Form<T> {
        /** Проверяет поля и сохраняет данные. {@code false} — есть ошибки, окно остаётся открытым. */
        boolean save();

        /** Сохранённый объект — результат диалога. */
        T result();

        /** Поле, которое получает фокус при открытии формы. */
        Node initialFocus();
    }

    private FormDialogs() { }

    public static <T> Optional<T> show(Window owner, String fxml, Form<T> form, String title, String header) {
        FXMLLoader loader = new FXMLLoader(FormDialogs.class.getResource(fxml));
        loader.setController(form);
        DialogPane pane;
        try {
            pane = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось загрузить форму " + fxml, e);
        }
        URL css = FormDialogs.class.getResource("/css/app.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
        pane.setHeaderText(header);

        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().setAll(save, cancel);
        Button saveButton = (Button) pane.lookupButton(save);
        saveButton.getStyleClass().add("btn-primary");
        pane.lookupButton(cancel).getStyleClass().add("btn-secondary");

        Dialog<T> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setDialogPane(pane);
        dialog.setResizable(true);
        if (owner != null) dialog.initOwner(owner);

        // «Сохранить» с ошибками не закрывает окно: событие гасится, а под полями появляются сообщения.
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (!form.save()) {
                event.consume();
                pane.getScene().getWindow().sizeToScene();
            }
        });
        dialog.setResultConverter(button -> button == save ? form.result() : null);
        dialog.setOnShown(e -> Platform.runLater(() -> form.initialFocus().requestFocus()));
        return dialog.showAndWait();
    }
}
