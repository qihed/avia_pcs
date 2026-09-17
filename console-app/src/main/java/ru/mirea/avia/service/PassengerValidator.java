package ru.mirea.avia.service;

import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.dto.PassengerDtos.PassengerRequest;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Проверяет и нормализует данные карточки пассажира.
 *
 * <p>Методы отдельных полей вызываются диалогами ввода сразу после каждого значения,
 * а {@link #validate(PassengerRequest)} — сервисом перед сохранением карточки целиком.</p>
 */
public final class PassengerValidator {
    private static final int MAX_NAME_LENGTH = 60;
    private static final int MAX_EMAIL_LENGTH = 120;
    private static final Pattern NAME = Pattern.compile("^[A-Za-zА-Яа-яЁё][A-Za-zА-Яа-яЁё' -]*$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d{10,15}$");
    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private final Clock clock;

    public PassengerValidator(Clock clock) {
        this.clock = clock;
    }

    /** Проверяет фамилию, имя или отчество, сжимает пробелы и делает первую букву заглавной. */
    public String requireName(String value, NameField field) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new ValidationException(ErrorCode.E_104,
                    field.getTitle() + " пассажира не может быть " + field.getEmptyWord());
        }
        if (trimmed.length() > MAX_NAME_LENGTH || !NAME.matcher(trimmed).matches()) {
            throw new ValidationException(ErrorCode.E_103, "Поле «" + field.getTitle()
                    + "» может содержать только буквы, пробел, дефис и апостроф"
                    + " (до " + MAX_NAME_LENGTH + " символов)");
        }
        return capitalize(trimmed);
    }

    /** Проверяет необязательное поле ФИО: пустое значение превращается в {@code null}. */
    public String optionalName(String value, NameField field) {
        return value == null || value.isBlank() ? null : requireName(value, field);
    }

    /** Проверяет, что дата рождения не в будущем и не раньше 01.01.1900. */
    public LocalDate checkBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new ValidationException(ErrorCode.E_104, "Дата рождения не может быть пустой");
        }
        if (birthDate.isAfter(LocalDate.now(clock))) {
            throw new ValidationException(ErrorCode.E_102, "Дата рождения не может быть в будущем");
        }
        if (birthDate.isBefore(MIN_BIRTH_DATE)) {
            throw new ValidationException(ErrorCode.E_102, "Дата рождения не может быть раньше 01.01.1900");
        }
        return birthDate;
    }

    /** Проверяет номер по формату типа документа и возвращает его в хранимом виде. */
    public String normalizeDocument(DocumentType type, String number) {
        if (type == null) throw new ValidationException(ErrorCode.E_104, "Тип документа не выбран");
        if (number == null || number.isBlank()) {
            throw new ValidationException(ErrorCode.E_104, "Номер документа не может быть пустым");
        }
        if (!type.matches(number)) {
            throw new ValidationException(ErrorCode.E_103, "Номер документа «" + type.getTitle()
                    + "» должен иметь вид " + type.getFormatHint());
        }
        return type.normalize(number);
    }

    /** Проверяет формат e-mail и приводит адрес к нижнему регистру. */
    public String normalizeEmail(String email) {
        String trimmed = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            throw new ValidationException(ErrorCode.E_104, "E-mail не может быть пустым");
        }
        if (trimmed.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(trimmed).matches()) {
            throw new ValidationException(ErrorCode.E_103, "E-mail должен иметь вид name@example.com");
        }
        return trimmed;
    }

    /** Проверяет телефон и удаляет из него пробелы, скобки и дефисы. */
    public String normalizePhone(String phone) {
        String compact = phone == null ? "" : phone.replaceAll("[\\s()\\-]", "");
        if (compact.isEmpty()) {
            throw new ValidationException(ErrorCode.E_104, "Телефон не может быть пустым");
        }
        if (!PHONE.matcher(compact).matches()) {
            throw new ValidationException(ErrorCode.E_103,
                    "Телефон должен содержать 10–15 цифр, например +79161234567");
        }
        return compact;
    }

    /** Проверяет карточку целиком и возвращает копию запроса с нормализованными полями. */
    public PassengerRequest validate(PassengerRequest request) {
        String lastName = requireName(request.lastName(), NameField.LAST_NAME);
        String firstName = requireName(request.firstName(), NameField.FIRST_NAME);
        String middleName = optionalName(request.middleName(), NameField.MIDDLE_NAME);
        LocalDate birthDate = checkBirthDate(request.birthDate());
        String documentNumber = normalizeDocument(request.documentType(), request.documentNumber());
        return new PassengerRequest(lastName, firstName, middleName, birthDate, request.documentType(),
                documentNumber, normalizeEmail(request.email()), normalizePhone(request.phone()));
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    /** Поле ФИО: название для сообщений и согласованная форма слова «пустой». */
    public enum NameField {
        LAST_NAME("Фамилия", "пустой"),
        FIRST_NAME("Имя", "пустым"),
        MIDDLE_NAME("Отчество", "пустым");

        private final String title;
        private final String emptyWord;

        NameField(String title, String emptyWord) {
            this.title = title;
            this.emptyWord = emptyWord;
        }

        public String getTitle() { return title; }
        public String getEmptyWord() { return emptyWord; }
    }
}
