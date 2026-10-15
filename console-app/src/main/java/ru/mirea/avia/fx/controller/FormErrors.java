package ru.mirea.avia.fx.controller;

import javafx.beans.Observable;
import javafx.scene.control.Control;
import javafx.scene.control.Label;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ошибки формы: красный текст под полем и красная рамка у самого поля, плюс общая плашка сверху
 * для ошибок сервиса (бизнес-правила, БД). Ошибка поля исчезает, как только пользователь его меняет.
 */
public final class FormErrors {
    private static final String INVALID = "invalid";

    private final Map<Control, Label> fields = new LinkedHashMap<>();
    private final Label banner;

    public FormErrors(Label banner) {
        this.banner = banner;
        hideWhenEmpty(banner);
    }

    /** Связывает поле с его меткой ошибки; {@code edits} — свойства, изменение которых снимает ошибку. */
    public void register(Control field, Label errorLabel, Observable... edits) {
        fields.put(field, errorLabel);
        hideWhenEmpty(errorLabel);
        for (Observable edit : edits) {
            edit.addListener(o -> {
                clear(field);
                banner.setText("");
            });
        }
    }

    /** Ставит ошибку полю; если у поля уже есть ошибка, остаётся первая. */
    public void set(Control field, String message) {
        if (has(field)) return;
        fields.get(field).setText(message);
        if (!field.getStyleClass().contains(INVALID)) field.getStyleClass().add(INVALID);
    }

    public boolean has(Control field) {
        Label label = fields.get(field);
        return label != null && !label.getText().isEmpty();
    }

    public void clear(Control field) {
        Label label = fields.get(field);
        if (label != null) label.setText("");
        field.getStyleClass().remove(INVALID);
    }

    public void clearAll() {
        fields.keySet().forEach(this::clear);
        banner.setText("");
    }

    public boolean isEmpty() {
        return fields.keySet().stream().noneMatch(this::has);
    }

    public void showBanner(String message) {
        banner.setText(message == null || message.isBlank() ? "Не удалось сохранить данные." : message);
    }

    public void focusFirstInvalid() {
        fields.keySet().stream().filter(this::has).findFirst().ifPresent(Control::requestFocus);
    }

    private static void hideWhenEmpty(Label label) {
        label.setText("");
        label.visibleProperty().bind(label.textProperty().isNotEmpty());
        label.managedProperty().bind(label.visibleProperty());
    }
}
