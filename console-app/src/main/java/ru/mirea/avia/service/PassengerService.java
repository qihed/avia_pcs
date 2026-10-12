package ru.mirea.avia.service;

import ru.mirea.avia.exception.BusinessException;
import ru.mirea.avia.exception.EntityNotFoundException;
import ru.mirea.avia.model.DocumentType;
import ru.mirea.avia.model.Passenger;
import ru.mirea.avia.repository.PassengerRepository;
import ru.mirea.avia.util.Validators;

import java.time.LocalDate;
import java.sql.SQLException;
import java.util.List;

/** Ведение справочника пассажиров. */
public class PassengerService {
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
        return repository.create(passenger);
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
        if (passenger.getDocumentType() == null) {
            throw new BusinessException("Тип документа не выбран");
        }
        if (passenger.getDocumentNumber() == null || passenger.getDocumentNumber().isBlank()) {
            throw new BusinessException("Номер документа обязателен");
        }
        if (!Validators.isValidEmail(passenger.getEmail())) {
            throw new BusinessException("Некорректный email: нужен символ @ и окончание .com или .ru (например, ivan@mail.ru).");
        }
        if (!Validators.isValidPhone(passenger.getPhone())) {
            throw new BusinessException("Телефон должен состоять только из цифр, от 10 до 15 штук.");
        }
    }
}
