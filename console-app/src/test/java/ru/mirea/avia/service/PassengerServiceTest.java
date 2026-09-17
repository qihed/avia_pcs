package ru.mirea.avia.service;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.dto.PassengerDtos.PassengerRequest;
import ru.mirea.avia.dto.PassengerDtos.PassengerResponse;
import ru.mirea.avia.error.BusinessRuleException;
import ru.mirea.avia.error.EntityNotFoundException;
import ru.mirea.avia.error.ValidationException;
import ru.mirea.avia.service.PassengerValidator.NameField;
import ru.mirea.avia.support.TestContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет справочник пассажиров: нормализацию полей, уникальность документа и e-mail, правило BR-10. */
class PassengerServiceTest {
    private final TestContext ctx = new TestContext().withDemoData();
    private final PassengerService service = ctx.passengerService;
    private final PassengerValidator validator = service.validator();

    @Test
    void createNormalizesFields() {
        PassengerResponse passenger = service.create(request("45 01 111222", "Olga@Example.com"));
        assertThat(passenger.id()).isEqualTo(7);
        assertThat(passenger.lastName()).isEqualTo("Смирнова");
        assertThat(passenger.firstName()).isEqualTo("Ольга");
        assertThat(passenger.middleName()).isNull();
        assertThat(passenger.fullName()).isEqualTo("Смирнова Ольга");
        assertThat(passenger.shortName()).isEqualTo("Смирнова О.");
        assertThat(passenger.birthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(passenger.age()).isEqualTo(26);
        assertThat(passenger.documentNumber()).isEqualTo("4501111222");
        assertThat(passenger.maskedDocument()).isEqualTo("45 01 ****22");
        assertThat(passenger.email()).isEqualTo("olga@example.com");
        assertThat(passenger.phone()).isEqualTo("+79161234567");
        assertThat(passenger.createdAt()).isEqualTo(ctx.now);
        assertThat(service.count()).isEqualTo(7);
        assertThat(ctx.passengers.findById(7L).orElseThrow().getEmail()).isEqualTo("olga@example.com");
    }

    @Test
    void normalizesDocumentNumbersByType() {
        assertThat(validator.normalizeDocument(DocumentType.BIRTH_CERTIFICATE, " viii-мю-654321 "))
                .isEqualTo("VIII-МЮ-654321");
        assertThat(validator.normalizeDocument(DocumentType.INTERNATIONAL_PASSPORT, "75 0123457"))
                .isEqualTo("750123457");
        assertThat(validator.normalizeDocument(DocumentType.FOREIGN_DOCUMENT, "c01-x00-t48"))
                .isEqualTo("C01X00T48");
        assertThat(validator.normalizeEmail("  Olga@Example.COM ")).isEqualTo("olga@example.com");
        assertThat(validator.normalizePhone("8 (916) 000-00-00")).isEqualTo("89160000000");
    }

    @Test
    void rejectsDuplicateDocumentAndEmail() {
        assertThatThrownBy(() -> service.create(request("4510 123456", "new@example.com")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-307")
                .hasMessage("Пассажир с таким документом уже есть: Иванов И. И. (ID 1)");
        assertThatThrownBy(() -> service.create(request("4501111222", "IVANOV@example.com")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-307")
                .hasMessage("E-mail ivanov@example.com уже указан в карточке пассажира Иванов И. И. (ID 1)");
        assertThat(service.count()).isEqualTo(6);
    }

    @Test
    void checksUniquenessExceptOwnCard() {
        assertThat(service.checkDocumentFree(DocumentType.PASSPORT_RF, "4510123456", 1L)).isEqualTo("4510123456");
        assertThat(service.checkDocumentFree(DocumentType.INTERNATIONAL_PASSPORT, "4510123456", null))
                .isEqualTo("4510123456");
        assertThatThrownBy(() -> service.checkDocumentFree(DocumentType.PASSPORT_RF, "4510123456", 2L))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-307");
        assertThat(service.checkEmailFree("IVANOV@EXAMPLE.COM", 1L)).isEqualTo("IVANOV@EXAMPLE.COM");
        assertThatThrownBy(() -> service.checkEmailFree("ivanov@example.com", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-307");
    }

    @Test
    void updatesPassengerKeepingOwnDocumentAndEmail() {
        PassengerRequest current = service.get(1).toRequest();
        PassengerRequest changed = new PassengerRequest(current.lastName(), current.firstName(), " ",
                current.birthDate(), current.documentType(), current.documentNumber(), current.email(),
                "8 916 000-00-00");
        PassengerResponse updated = service.update(1, changed);
        assertThat(updated.phone()).isEqualTo("89160000000");
        assertThat(updated.middleName()).isNull();
        assertThat(updated.shortName()).isEqualTo("Иванов И.");
        assertThat(ctx.passengers.findById(1L).orElseThrow().getPhone()).isEqualTo("89160000000");

        PassengerRequest stolenEmail = new PassengerRequest(current.lastName(), current.firstName(), null,
                current.birthDate(), current.documentType(), current.documentNumber(), "petrova@example.com",
                current.phone());
        assertThatThrownBy(() -> service.update(1, stolenEmail))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-307");
        assertThatThrownBy(() -> service.update(99, changed))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-202")
                .hasMessage("Пассажир с ID 99 не найден");
    }

    @Test
    void explainsBlankAndMalformedNames() {
        assertThatThrownBy(() -> validator.requireName(" ", NameField.LAST_NAME))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Фамилия пассажира не может быть пустой");
        assertThatThrownBy(() -> validator.requireName("", NameField.FIRST_NAME))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Имя пассажира не может быть пустым");
        assertThatThrownBy(() -> validator.requireName(null, NameField.MIDDLE_NAME))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Отчество пассажира не может быть пустым");
        assertThatThrownBy(() -> validator.requireName("R2D2", NameField.LAST_NAME))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Поле «Фамилия» может содержать только буквы, пробел, дефис и апостроф (до 60 символов)");
        assertThatThrownBy(() -> validator.requireName("а".repeat(61), NameField.FIRST_NAME))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103");
        assertThat(validator.optionalName("  ", NameField.MIDDLE_NAME)).isNull();
        assertThat(validator.requireName("  анна-мария   о'нил ", NameField.FIRST_NAME)).isEqualTo("Анна-мария о'нил");
    }

    @Test
    void createRejectsBlankFirstName() {
        PassengerRequest blank = new PassengerRequest("Смирнова", "  ", null, LocalDate.of(2000, 1, 1),
                DocumentType.PASSPORT_RF, "4501111222", "olga@example.com", "+79161234567");
        assertThatThrownBy(() -> service.create(blank))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessageContaining("Имя пассажира не может быть пустым");
        assertThat(service.count()).isEqualTo(6);
    }

    @Test
    void explainsInvalidBirthDateContactsAndDocument() {
        assertThatThrownBy(() -> validator.checkBirthDate(ctx.now.toLocalDate().plusDays(1)))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-102")
                .hasMessage("Дата рождения не может быть в будущем");
        assertThatThrownBy(() -> validator.checkBirthDate(LocalDate.of(1899, 12, 31)))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-102")
                .hasMessage("Дата рождения не может быть раньше 01.01.1900");
        assertThatThrownBy(() -> validator.checkBirthDate(null))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Дата рождения не может быть пустой");
        assertThatThrownBy(() -> validator.normalizeEmail("ivanov@"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("E-mail должен иметь вид name@example.com");
        assertThatThrownBy(() -> validator.normalizeEmail(" "))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("E-mail не может быть пустым");
        assertThatThrownBy(() -> validator.normalizePhone("12345"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Телефон должен содержать 10–15 цифр, например +79161234567");
        assertThatThrownBy(() -> validator.normalizePhone(""))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Телефон не может быть пустым");
    }

    @Test
    void explainsInvalidDocument() {
        assertThatThrownBy(() -> validator.normalizeDocument(DocumentType.PASSPORT_RF, "12345"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Номер документа «паспорт РФ» должен иметь вид 4510 123456 (10 цифр)");
        assertThatThrownBy(() -> validator.normalizeDocument(null, "4510123456"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Тип документа не выбран");
        assertThatThrownBy(() -> validator.normalizeDocument(DocumentType.PASSPORT_RF, " "))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Номер документа не может быть пустым");
    }

    @Test
    void deletesOnlyPassengerWithoutBookings() {
        assertThat(service.countBookings(1)).isEqualTo(3);
        assertThatThrownBy(() -> service.checkCanDelete(1))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-305")
                .hasMessage("Нельзя удалить пассажира Иванов И. И.: у него есть бронирования (3)");
        assertThatThrownBy(() -> service.delete(2))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-305")
                .hasMessage("Нельзя удалить пассажира Петрова А. С.: у него есть бронирования (3)");

        long id = service.create(request("4501111222", "olga@example.com")).id();
        assertThat(service.countBookings(id)).isZero();
        assertThatCode(() -> service.checkCanDelete(id)).doesNotThrowAnyException();
        service.delete(id);
        assertThat(service.count()).isEqualTo(6);
        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-202")
                .hasMessage("Пассажир с ID 7 не найден");
        assertThatThrownBy(() -> service.delete(99))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-202");
    }

    @Test
    void listsPassengersWithMaskedDocuments() {
        assertThat(service.list())
                .extracting(PassengerResponse::lastName)
                .containsExactly("Иванов", "Петрова", "Сидоров", "Кузнецова", "Соколов", "Новиков");
        assertThat(service.list())
                .extracting(PassengerResponse::maskedDocument)
                .containsExactly("45 10 ****56", "45 12 ****21", "45 12 ****67", "75*****56", "VIII-MU-****56",
                        "C0*****47");
        assertThat(service.get(1).age()).isEqualTo(41);
        assertThat(service.get(1).fullName()).isEqualTo("Иванов Иван Иванович");
    }

    private static PassengerRequest request(String documentNumber, String email) {
        return new PassengerRequest("  смирнова ", "ольга", " ", LocalDate.of(2000, 1, 1),
                DocumentType.PASSPORT_RF, documentNumber, email, "+7 (916) 123-45-67");
    }
}
