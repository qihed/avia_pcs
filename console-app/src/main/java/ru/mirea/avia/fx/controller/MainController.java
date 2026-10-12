package ru.mirea.avia.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** Каркас главного окна: слева меню, в центре текущий экран, снизу строка с подключением к БД. */
public final class MainController {
    @FXML private StackPane contentPane;
    @FXML private Label connectionBadge;

    public void show(Parent view, String connection) {
        contentPane.getChildren().setAll(view);
        connectionBadge.setText(connection);
    }
}
