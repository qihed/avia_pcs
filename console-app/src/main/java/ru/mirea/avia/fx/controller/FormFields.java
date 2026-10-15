package ru.mirea.avia.fx.controller;

import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Мелкие помощники для полей форм: ограничение ввода, формат дат и времени, подписи enum в ComboBox. */
public final class FormFields {
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final Locale RU = Locale.forLanguageTag("ru-RU");

    private FormFields() { }

    /** Пропускает в поле только текст, целиком подходящий под шаблон (проверяется результат правки). */
    public static void restrict(TextField field, String regex) {
        Pattern pattern = Pattern.compile(regex);
        field.setTextFormatter(new TextFormatter<String>(change ->
                pattern.matcher(change.getControlNewText()).matches() ? change : null));
    }

    /** Только цифры, не больше {@code max}: из вставленного текста («+7 916 123-45-67») остальное выбрасывается. */
    public static void digits(TextField field, int max) {
        field.setTextFormatter(new TextFormatter<String>(change -> {
            change.setText(change.getText().replaceAll("\\D", ""));
            return change.getControlNewText().length() <= max ? change : null;
        }));
    }

    /** Как {@link #restrict}, но вводимые буквы сразу переводятся в верхний регистр (коды рейсов, аэропортов). */
    public static void upperCase(TextField field, String regex) {
        Pattern pattern = Pattern.compile(regex);
        field.setTextFormatter(new TextFormatter<String>(change -> {
            change.setText(change.getText().toUpperCase(RU));
            return pattern.matcher(change.getControlNewText()).matches() ? change : null;
        }));
    }

    /** Текст поля без пробелов по краям; пустая строка вместо null. */
    public static String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    /** DatePicker с форматом дд.мм.гггг и запретом выбирать в календаре дни, не подходящие под условие. */
    public static void setupDatePicker(DatePicker picker, Predicate<LocalDate> allowed) {
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : DATE.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                return text == null || text.isBlank() ? null : LocalDate.parse(text.trim(), DATE);
            }
        });
        picker.setPromptText("дд.мм.гггг");
        if (allowed != null) {
            picker.setDayCellFactory(p -> new DateCell() {
                @Override
                public void updateItem(LocalDate date, boolean empty) {
                    super.updateItem(date, empty);
                    setDisable(empty || date == null || !allowed.test(date));
                }
            });
        }
    }

    /**
     * Дата из DatePicker с учётом текста, набранного вручную и ещё не подтверждённого Enter.
     * Бросает {@link DateTimeParseException}, если текст не похож на дату.
     */
    public static LocalDate date(DatePicker picker) {
        String typed = picker.getEditor().getText();
        if (typed == null || typed.isBlank()) {
            // Пустой текст: либо поле очистили (тогда значение уже null), либо текст ещё не синхронизирован.
            return picker.getValue();
        }
        LocalDate value = LocalDate.parse(typed.trim(), DATE);
        if (!value.equals(picker.getValue())) picker.setValue(value);
        return value;
    }

    /** Время «ЧЧ:ММ»; null для пустого поля, {@link DateTimeParseException} для неверного. */
    public static LocalTime time(TextField field) {
        String text = text(field);
        return text.isEmpty() ? null : LocalTime.parse(text, TIME);
    }

    public static String format(LocalTime time) {
        return time == null ? "" : String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    /** Подписи значений enum в ComboBox (getTitle вместо name). */
    public static <T> StringConverter<T> titles(Function<T, String> title) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : title.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }
}
