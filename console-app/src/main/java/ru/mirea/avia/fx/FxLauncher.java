package ru.mirea.avia.fx;

import javafx.application.Application;

import java.util.Locale;

/**
 * Точка входа JavaFX-клиента. Это обычный класс (не наследник {@code Application}), поэтому приложение
 * запускается и из IDE, и через {@code ./mvnw javafx:run} без ошибки «JavaFX runtime components are missing».
 */
public final class FxLauncher {
    private FxLauncher() { }

    public static void main(String[] args) {
        // Русская локаль: кнопки стандартных диалогов, календарь DatePicker.
        Locale.setDefault(Locale.forLanguageTag("ru-RU"));
        Application.launch(MainApp.class, args);
    }
}
