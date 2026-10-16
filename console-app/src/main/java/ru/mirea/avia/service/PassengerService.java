package ru.mirea.avia.service;

import ru.mirea.avia.exception.BusinessException;
import ru.mirea.avia.exception.EntityNotFoundException;
import ru.mirea.avia.model.DocumentType;
import ru.mirea.avia.model.Passenger;
import ru.mirea.avia.repository.PassengerRepository;
import ru.mirea.avia.util.Validators;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/** Ведение справочника пассажиров. */
public class PassengerService {
    /** С этого возраста у гражданина РФ должен быть паспорт, до него — свидетельство о рождении. */
    public static final int PASSPORT_AGE = 14;
    public static final int MAX_AGE = 120;

    private final PassengerRepository repository;

    public PassengerService(PassengerRepository repository) {
        this.repository = repository;
    }

    public Passenger create(String lastName, String firstName, String middleName, LocalDate birthDate,
                            DocumentType documentType, String documentNumber, String email,
                            String phone) throws SQLException {
        Passenger passenger = new Passenger(lastName, firstName, middleName, birthDate,
                documentType, documentNumber, email, phone);
        validate(passenger);
        requireUniqueEmail(passenger);
        return repository.create(passenger);
    }

    /** Изменение данных пассажира (форма «Изменить»). ID и дата регистрации не меняются. */
    public Passenger update(long id, String lastName, String firstName, String middleName, LocalDate birthDate,
                            DocumentType documentType, String documentNumber, String email,
                            String phone) throws SQLException {
        Passenger current = get(id);
        Passenger passenger = new Passenger(id, lastName, firstName, middleName, birthDate,
                documentType, documentNumber, email, phone, current.getCreatedAt());
        validate(passenger);
        requireUniqueEmail(passenger);
        return repository.update(passenger);
    }

    public List<Passenger> list() throws SQLException {
        return repository.findAll();
    }

    public Passenger get(long id) throws SQLException {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Пассажир с ID " + id + " не найден"));
    }

    public long count() throws SQLException {
        return repository.count();
    }

    /** Возвращает пассажира, если его можно удалить; экран вызывает до запроса подтверждения. */
    public Passenger requireDeletable(long id) throws SQLException {
        Passenger passenger = get(id);
        int bookings = repository.countBookings(id);
        // Бизнес-правило: пассажира, на которого оформлены брони, удалить нельзя (в БД то же держит внешний ключ).
        if (bookings > 0) {
            throw new BusinessException("Нельзя удалить пассажира " + passenger.getShortName()
                    + ": на него оформлено броней — " + bookings + ". Сначала удалите его брони.");
        }
        return passenger;
    }

    public void delete(long id) throws SQLException {
        requireDeletable(id);
        if (!repository.deleteById(id)) {
            throw new EntityNotFoundException("Пассажир с ID " + id + " не найден");
        }
    }

    /**
     * Проверка соответствия типа документа возрасту. Возвращает текст ошибки или {@code null}, если всё в порядке —
     * этим же методом пользуется форма пассажира, чтобы показать ошибку прямо у поля.
     */
    public static String checkDocumentForAge(DocumentType type, LocalDate birthDate) {
        if (type == null || birthDate == null) return null;
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        if (type == DocumentType.PASSPORT_RF && age < PASSPORT_AGE) {
            return "Паспорт РФ выдаётся с " + PASSPORT_AGE + " лет — для ребёнка укажите свидетельство о рождении.";
        }
        if (type == DocumentType.BIRTH_CERTIFICATE && age >= PASSPORT_AGE) {
            return "Свидетельство о рождении действует только до " + PASSPORT_AGE + " лет.";
        }
        return null;
    }

    private void requireUniqueEmail(Passenger passenger) throws SQLException {
        if (repository.existsByEmail(passenger.getEmail(), passenger.getId())) {
            throw new BusinessException("Пассажир с e-mail " + passenger.getEmail() + " уже зарегистрирован.");
        }
    }

    private void validate(Passenger passenger) {
        if (passenger.getLastName() != null && passenger.getLastName().length() > 60) {
            throw new BusinessException("Фамилия не должна превышать 60 символов.");
        }

        if (passenger.getFirstName() != null && passenger.getFirstName().length() > 60) {
            throw new BusinessException("Имя не должно превышать 60 символов.");
        }

        if (passenger.getMiddleName() != null && passenger.getMiddleName().length() > 60) {
            throw new BusinessException("Отчество не должно превышать 60 символов.");
        }
        if (passenger.getLastName() == null || passenger.getLastName().isBlank()) {
            throw new BusinessException("Фамилия обязательна");
        }
        if (passenger.getFirstName() == null || passenger.getFirstName().isBlank()) {
            throw new BusinessException("Имя обязательно");
        }
        if (!Validators.isValidName(passenger.getLastName())) {
            throw new BusinessException("Фамилия должна начинаться с заглавной буквы и содержать только буквы.");
        }
        if (!Validators.isValidName(passenger.getFirstName())) {
            throw new BusinessException("Имя должно начинаться с заглавной буквы и содержать только буквы.");
        }
        if (passenger.getMiddleName() != null && !passenger.getMiddleName().isBlank()
                && !Validators.isValidName(passenger.getMiddleName())) {
            throw new BusinessException("Отчество должно начинаться с заглавной буквы и содержать только буквы.");
        }
        if (passenger.getBirthDate() == null || passenger.getBirthDate().isAfter(LocalDate.now())) {
            throw new BusinessException("Дата рождения обязательна и не может быть в будущем");
        }
        if (passenger.getBirthDate().isBefore(LocalDate.now().minusYears(MAX_AGE))) {
            throw new BusinessException("Проверьте дату рождения: возраст больше " + MAX_AGE + " лет.");
        }
        if (passenger.getDocumentType() == null) {
            throw new BusinessException("Тип документа не выбран");
        }
        if (passenger.getDocumentNumber() == null || passenger.getDocumentNumber().isBlank()) {
            throw new BusinessException("Номер документа обязателен");
        }
        if (!Validators.isValidDocumentNumber(passenger.getDocumentType(), passenger.getDocumentNumber())) {
            throw new BusinessException("Неверный номер документа «" + passenger.getDocumentType().getTitle() + "»: "
                    + Validators.documentNumberHint(passenger.getDocumentType()) + ".");
        }
        String documentError = checkDocumentForAge(passenger.getDocumentType(), passenger.getBirthDate());
        if (documentError != null) {
            throw new BusinessException(documentError);
        }
        if (!Validators.isValidEmail(passenger.getEmail())) {
            throw new BusinessException("Некорректный email: нужен символ @ и окончание .com или .ru (например, ivan@mail.ru).");
        }
        if (passenger.getEmail().length() > 120) {
            throw new BusinessException("E-mail не должен превышать 120 символов.");
        }
        if (!Validators.isValidPhone(passenger.getPhone())) {
            throw new BusinessException("Телефон должен состоять только из цифр, от 10 до 15 штук.");
        }
    }
}
