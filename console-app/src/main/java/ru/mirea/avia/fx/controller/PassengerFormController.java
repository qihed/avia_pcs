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
import ru.mirea.avia.model.DocumentType;
import ru.mirea.avia.model.Passenger;
import ru.mirea.avia.service.PassengerService;
import ru.mirea.avia.util.Validators;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Форма пассажира (PassengerFormDialog): добавление и изменение. Поля проверяются здесь, ошибка выводится
 * под конкретным полем; сохранение и бизнес-правила — в {@link PassengerService}, его ошибки — плашкой сверху.
 *
 * <pre>
 * // в экране «Пассажиры»:
 * PassengerFormController.open(table.getScene().getWindow(), passengers, null)        // добавить
 *         .ifPresent(saved -> onRefresh());
 * PassengerFormController.open(table.getScene().getWindow(), passengers, selected)    // изменить
 *         .ifPresent(saved -> onRefresh());
 * </pre>
 */
public final class PassengerFormController implements FormDialogs.Form<Passenger> {
    private static final int MAX_NAME = 60;

    @FXML private Label formError;
    @FXML private TextField lastNameField;
    @FXML private Label lastNameError;
    @FXML private TextField firstNameField;
    @FXML private Label firstNameError;
    @FXML private TextField middleNameField;
    @FXML private Label middleNameError;
    @FXML private DatePicker birthDatePicker;
    @FXML private Label birthDateError;
    @FXML private ComboBox<DocumentType> documentTypeCombo;
    @FXML private Label documentTypeError;
    @FXML private TextField documentNumberField;
    @FXML private Label documentNumberHint;
    @FXML private Label documentNumberError;
    @FXML private TextField emailField;
    @FXML private Label emailError;
    @FXML private TextField phoneField;
    @FXML private Label phoneError;

    private final PassengerService passengers;
    /** Редактируемый пассажир; {@code null} — форма добавления. */
    private final Passenger original;
    private FormErrors errors;
    private Passenger saved;

    public PassengerFormController(PassengerService passengers, Passenger original) {
        this.passengers = passengers;
        this.original = original;
    }

    /**
     * Открывает модальную форму. {@code passenger == null} — новый пассажир, иначе изменение существующего.
     * Возвращает сохранённого пассажира или пустой Optional, если нажата «Отмена».
     */
    public static Optional<Passenger> open(Window owner, PassengerService passengers, Passenger passenger) {
        PassengerFormController form = new PassengerFormController(passengers, passenger);
        String title = passenger == null ? "Новый пассажир" : "Изменение пассажира";
        String header = passenger == null ? "Новый пассажир"
                : "Пассажир №" + passenger.getId() + " · " + passenger.getShortName();
        return FormDialogs.show(owner, "/fxml/PassengerFormDialog.fxml", form, title, header);
    }

    @FXML
    private void initialize() {
        errors = new FormErrors(formError);
        errors.register(lastNameField, lastNameError, lastNameField.textProperty());
        errors.register(firstNameField, firstNameError, firstNameField.textProperty());
        errors.register(middleNameField, middleNameError, middleNameField.textProperty());
        errors.register(birthDatePicker, birthDateError,
                birthDatePicker.valueProperty(), birthDatePicker.getEditor().textProperty());
        errors.register(documentTypeCombo, documentTypeError, documentTypeCombo.valueProperty());
        errors.register(documentNumberField, documentNumberError,
                documentNumberField.textProperty(), documentTypeCombo.valueProperty());
        errors.register(emailField, emailError, emailField.textProperty());
        errors.register(phoneField, phoneError, phoneField.textProperty());

        // Ограничения ввода: лишнего символа просто не напечатать.
        FormFields.restrict(lastNameField, "[\\p{L}-]{0," + MAX_NAME + "}");
        FormFields.restrict(firstNameField, "[\\p{L}-]{0," + MAX_NAME + "}");
        FormFields.restrict(middleNameField, "[\\p{L}-]{0," + MAX_NAME + "}");
        FormFields.documentNumber(documentNumberField, documentTypeCombo.valueProperty());
        FormFields.restrict(emailField, "\\S{0,120}");
        FormFields.digits(phoneField, 15);

        FormFields.setupDatePicker(birthDatePicker, date -> !date.isAfter(LocalDate.now()));
        documentTypeCombo.getItems().setAll(DocumentType.values());
        documentTypeCombo.setConverter(FormFields.titles(DocumentType::getTitle));
        // Подсказка о формате номера меняется вместе с типом документа.
        documentNumberHint.textProperty().bind(documentTypeCombo.valueProperty().map(Validators::documentNumberHint));
        documentNumberHint.visibleProperty().bind(documentTypeCombo.valueProperty().isNotNull());
        documentNumberHint.managedProperty().bind(documentNumberHint.visibleProperty());

        if (original != null) fill(original);
        else documentTypeCombo.setValue(DocumentType.PASSPORT_RF);
    }

    private void fill(Passenger p) {
        lastNameField.setText(p.getLastName());
        firstNameField.setText(p.getFirstName());
        middleNameField.setText(p.getMiddleName() == null ? "" : p.getMiddleName());
        birthDatePicker.setValue(p.getBirthDate());
        documentTypeCombo.setValue(p.getDocumentType());
        documentNumberField.setText(p.getDocumentNumber());
        emailField.setText(p.getEmail());
        // В демо-данных телефоны записаны с «+»; поле принимает только цифры, поэтому лишнее отбрасывается.
        phoneField.setText(p.getPhone() == null ? "" : p.getPhone().replaceAll("\\D", ""));
        errors.clearAll();
    }

    @Override
    public boolean save() {
        errors.clearAll();
        LocalDate birthDate = readBirthDate();
        String lastName = FormFields.text(lastNameField);
        String firstName = FormFields.text(firstNameField);
        String middleName = FormFields.text(middleNameField);
        DocumentType type = documentTypeCombo.getValue();
        String documentNumber = FormFields.text(documentNumberField).replace(" ", "");   // пробел в паспорте РФ — только для вида
        String email = FormFields.text(emailField);
        String phone = FormFields.text(phoneField);

        checkName(lastNameField, lastName, "фамилию", true);
        checkName(firstNameField, firstName, "имя", true);
        checkName(middleNameField, middleName, "отчество", false);

        if (birthDate == null && !errors.has(birthDatePicker)) {
            errors.set(birthDatePicker, "Укажите дату рождения.");
        } else if (birthDate != null && birthDate.isAfter(LocalDate.now())) {
            errors.set(birthDatePicker, "Дата рождения не может быть в будущем.");
        } else if (birthDate != null && birthDate.isBefore(LocalDate.now().minusYears(PassengerService.MAX_AGE))) {
            errors.set(birthDatePicker, "Проверьте год: возраст больше " + PassengerService.MAX_AGE + " лет.");
        }

        if (type == null) {
            errors.set(documentTypeCombo, "Выберите тип документа.");
        } else {
            String ageError = PassengerService.checkDocumentForAge(type, birthDate);
            if (ageError != null) errors.set(documentTypeCombo, ageError);
        }
        if (documentNumber.isEmpty()) {
            errors.set(documentNumberField, "Укажите номер документа.");
        } else if (type != null && !Validators.isValidDocumentNumber(type, documentNumber)) {
            errors.set(documentNumberField, "Неверный формат: " + Validators.documentNumberHint(type) + ".");
        }

        if (email.isEmpty()) {
            errors.set(emailField, "Укажите e-mail.");
        } else if (!Validators.isValidEmail(email)) {
            errors.set(emailField, Validators.EMAIL_HINT);
        }
        if (phone.isEmpty()) {
            errors.set(phoneField, "Укажите телефон.");
        } else if (!Validators.isValidPhone(phone)) {
            errors.set(phoneField, "Только цифры, от 10 до 15 штук, например 89161234567.");
        }

        if (!errors.isEmpty()) {
            errors.focusFirstInvalid();
            return false;
        }

        String middle = middleName.isEmpty() ? null : middleName;
        try {
            saved = original == null
                    ? passengers.create(lastName, firstName, middle, birthDate, type, documentNumber, email, phone)
                    : passengers.update(original.getId(), lastName, firstName, middle, birthDate, type,
                    documentNumber, email, phone);
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

    private LocalDate readBirthDate() {
        try {
            return FormFields.date(birthDatePicker);
        } catch (DateTimeParseException e) {
            errors.set(birthDatePicker, "Дата в формате дд.мм.гггг, например 12.04.1985.");
            return null;
        }
    }

    private void checkName(TextField field, String value, String what, boolean required) {
        if (value.isEmpty()) {
            if (required) errors.set(field, "Укажите " + what + ".");
        } else if (!Validators.isValidName(value)) {
            errors.set(field, "С заглавной буквы, только буквы; двойные — через дефис (Римский-Корсаков).");
        }
    }

    @Override
    public Passenger result() {
        return saved;
    }

    @Override
    public Node initialFocus() {
        return lastNameField;
    }
}
