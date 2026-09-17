package ru.mirea.avia.ui;

import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.dto.PassengerDtos.PassengerRequest;
import ru.mirea.avia.dto.PassengerDtos.PassengerResponse;
import ru.mirea.avia.service.PassengerService;
import ru.mirea.avia.service.PassengerValidator;
import ru.mirea.avia.service.PassengerValidator.NameField;
import ru.mirea.avia.util.DateTimeFormat;
import ru.mirea.avia.util.UiText;

import java.time.LocalDate;
import java.util.List;

/**
 * Формирует запрос создания или изменения карточки пассажира (UC-01).
 *
 * <p>Каждое поле проверяется сразу после ввода, в том числе на уникальность документа
 * и e-mail (E-307), поэтому при ошибке повторно запрашивается только это поле. Перед
 * сохранением сервис ещё раз проверяет карточку целиком.</p>
 */
public final class PassengerDialog {
    private static final String CLEAR = "-";
    private static final List<DocumentType> DOCUMENT_TYPES = List.of(DocumentType.values());

    private PassengerDialog() { }

    /** Запрашивает поля новой карточки и возвращает проверенный запрос. */
    public static PassengerRequest create(ConsoleIo io, InputPrompt prompt, PassengerService passengers) {
        Ui.section(io, "НОВЫЙ ПАССАЖИР");
        PassengerValidator validator = passengers.validator();
        String lastName = prompt.read("Фамилия", line -> validator.requireName(line, NameField.LAST_NAME));
        String firstName = prompt.read("Имя", line -> validator.requireName(line, NameField.FIRST_NAME));
        String middleName = prompt.read("Отчество (Enter — нет)",
                line -> validator.optionalName(line, NameField.MIDDLE_NAME));
        LocalDate birthDate = prompt.read("Дата рождения (" + DateTimeFormat.DATE_PATTERN + ")",
                line -> validator.checkBirthDate(DateTimeFormat.parseDate(line)));
        DocumentType type = prompt.readChoice("Тип документа", DOCUMENT_TYPES, UiText::documentType);
        String number = prompt.read(numberLabel(type), line -> checkDocument(passengers, type, line, null));
        String email = prompt.read("E-mail (name@example.com)",
                line -> passengers.checkEmailFree(validator.normalizeEmail(line), null));
        String phone = prompt.read("Телефон (например, +79161234567)", validator::normalizePhone);
        return new PassengerRequest(lastName, firstName, middleName, birthDate, type, number, email, phone);
    }

    /** Запрашивает новые значения карточки; пустой ввод оставляет текущее значение поля. */
    public static PassengerRequest edit(ConsoleIo io, InputPrompt prompt, PassengerService passengers,
                                        PassengerResponse existing) {
        PassengerValidator validator = passengers.validator();
        Ui.info(io, "Пассажир: " + Cards.passengerSummary(existing) + "   (Enter — оставить без изменений)");
        io.println();
        String lastName = prompt.readOrKeep("Фамилия", existing.lastName(), existing.lastName(),
                line -> validator.requireName(line, NameField.LAST_NAME));
        String firstName = prompt.readOrKeep("Имя", existing.firstName(), existing.firstName(),
                line -> validator.requireName(line, NameField.FIRST_NAME));
        String middleName = prompt.readOrKeep("Отчество («-» — удалить)", existing.middleName(),
                existing.middleName() == null ? "нет" : existing.middleName(),
                line -> CLEAR.equals(line) ? null : validator.requireName(line, NameField.MIDDLE_NAME));
        LocalDate birthDate = prompt.readOrKeep("Дата рождения", existing.birthDate(),
                DateTimeFormat.format(existing.birthDate()),
                line -> validator.checkBirthDate(DateTimeFormat.parseDate(line)));
        Document document = editDocument(prompt, passengers, existing);
        String email = prompt.readOrKeep("E-mail", existing.email(), existing.email(),
                line -> passengers.checkEmailFree(validator.normalizeEmail(line), existing.id()));
        String phone = prompt.readOrKeep("Телефон", existing.phone(), existing.phone(), validator::normalizePhone);
        return new PassengerRequest(lastName, firstName, middleName, birthDate, document.type(),
                document.number(), email, phone);
    }

    private static Document editDocument(InputPrompt prompt, PassengerService passengers,
                                         PassengerResponse existing) {
        DocumentType type = prompt.readChoiceOrKeep("Тип документа", DOCUMENT_TYPES, UiText::documentType,
                existing.documentType());
        // Номер документа другого типа имеет другой формат, поэтому прежний номер оставить нельзя.
        if (type != existing.documentType()) {
            String number = prompt.read(numberLabel(type),
                    line -> checkDocument(passengers, type, line, existing.id()));
            return new Document(type, number);
        }
        String number = prompt.readOrKeep(numberLabel(type), existing.documentNumber(),
                type.format(existing.documentNumber()),
                line -> checkDocument(passengers, type, line, existing.id()));
        return new Document(type, number);
    }

    private static String checkDocument(PassengerService passengers, DocumentType type, String line,
                                        Long exceptId) {
        String number = passengers.validator().normalizeDocument(type, line);
        return passengers.checkDocumentFree(type, number, exceptId);
    }

    private static String numberLabel(DocumentType type) {
        return "Номер документа, формат " + type.getFormatHint();
    }

    /** Тип и номер документа, введённые в диалоге изменения. */
    private record Document(DocumentType type, String number) {
    }
}
