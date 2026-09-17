package ru.mirea.avia.service;

import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.domain.Passenger;
import ru.mirea.avia.dto.PassengerDtos.PassengerRequest;
import ru.mirea.avia.dto.PassengerDtos.PassengerResponse;
import ru.mirea.avia.error.BusinessRuleException;
import ru.mirea.avia.error.EntityNotFoundException;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.jdbc.Transactions;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.PassengerRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ведение справочника пассажиров (FR-03, UC-01).
 *
 * <p>Контролирует уникальность документа и e-mail (E-307) и запрещает удалять пассажира,
 * у которого есть бронирования (BR-10).</p>
 */
public class PassengerService {
    private final PassengerRepository passengers;
    private final BookingRepository bookings;
    private final PassengerValidator validator;
    private final PassengerMapper mapper;
    private final Transactions transactions;
    private final Clock clock;

    public PassengerService(PassengerRepository passengers, BookingRepository bookings,
                            PassengerValidator validator, PassengerMapper mapper,
                            Transactions transactions, Clock clock) {
        this.passengers = passengers;
        this.bookings = bookings;
        this.validator = validator;
        this.mapper = mapper;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Возвращает все карточки пассажиров в порядке ID. */
    public List<PassengerResponse> list() {
        return transactions.read(() -> passengers.findAll().stream()
                .map(mapper::toResponse)
                .toList());
    }

    /** Возвращает карточку пассажира по ID или бросает E-202. */
    public PassengerResponse get(long id) {
        return transactions.read(() -> mapper.toResponse(require(id)));
    }

    /** Возвращает число карточек в справочнике. */
    public long count() {
        return transactions.read(() -> passengers.count());
    }

    /** Возвращает число бронирований пассажира в любом статусе. */
    public long countBookings(long id) {
        return transactions.read(() -> bookings.countByPassengerId(id));
    }

    /** Создаёт карточку после проверки формата полей и уникальности документа и e-mail. */
    public PassengerResponse create(PassengerRequest request) {
        PassengerRequest normalized = validator.validate(request);
        return transactions.write(() -> {
            validateUnique(normalized, null);
            Passenger passenger = new Passenger(normalized.lastName(), normalized.firstName(),
                    normalized.birthDate(), normalized.documentType(), normalized.documentNumber(),
                    normalized.email(), normalized.phone());
            apply(passenger, normalized);
            passenger.setCreatedAt(LocalDateTime.now(clock));
            return mapper.toResponse(passengers.insert(passenger));
        });
    }

    /** Изменяет карточку с теми же проверками, что и при создании. */
    public PassengerResponse update(long id, PassengerRequest request) {
        PassengerRequest normalized = validator.validate(request);
        return transactions.write(() -> {
            Passenger passenger = require(id);
            validateUnique(normalized, id);
            apply(passenger, normalized);
            return mapper.toResponse(passengers.update(passenger));
        });
    }

    /** Проверяет правило BR-10 до запроса подтверждения у оператора (E-305). */
    public void checkCanDelete(long id) {
        transactions.read(() -> {
            validateNoBookings(require(id));
            return null;
        });
    }

    /** Удаляет карточку пассажира, у которого нет бронирований (BR-10). */
    public void delete(long id) {
        transactions.write(() -> {
            validateNoBookings(require(id));
            return passengers.deleteById(id);
        });
    }

    /**
     * Проверяет, что документ не указан в другой карточке (E-307), и возвращает номер без изменений.
     */
    public String checkDocumentFree(DocumentType type, String number, Long exceptId) {
        return transactions.read(() -> {
            validateDocumentFree(type, number, exceptId);
            return number;
        });
    }

    /** Проверяет, что e-mail не указан в другой карточке (E-307), и возвращает его без изменений. */
    public String checkEmailFree(String email, Long exceptId) {
        return transactions.read(() -> {
            validateEmailFree(email, exceptId);
            return email;
        });
    }

    /** Возвращает валидатор полей для пошаговых диалогов ввода. */
    public PassengerValidator validator() {
        return validator;
    }

    private Passenger require(long id) {
        return passengers.findById(id).orElseThrow(() -> EntityNotFoundException.passenger(id));
    }

    private void validateUnique(PassengerRequest request, Long exceptId) {
        validateDocumentFree(request.documentType(), request.documentNumber(), exceptId);
        validateEmailFree(request.email(), exceptId);
    }

    private void validateDocumentFree(DocumentType type, String number, Long exceptId) {
        passengers.findByDocumentTypeAndDocumentNumber(type, number)
                .filter(other -> !other.getId().equals(exceptId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(ErrorCode.E_307, "Пассажир с таким документом уже есть: "
                            + other.getShortName() + " (ID " + other.getId() + ")");
                });
    }

    private void validateEmailFree(String email, Long exceptId) {
        passengers.findByEmailIgnoreCase(email)
                .filter(other -> !other.getId().equals(exceptId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(ErrorCode.E_307, "E-mail " + email
                            + " уже указан в карточке пассажира " + other.getShortName()
                            + " (ID " + other.getId() + ")");
                });
    }

    private void validateNoBookings(Passenger passenger) {
        long count = bookings.countByPassengerId(passenger.getId());
        if (count > 0) {
            throw new BusinessRuleException(ErrorCode.E_305, "Нельзя удалить пассажира "
                    + passenger.getShortName() + ": у него есть бронирования (" + count + ")");
        }
    }

    private static void apply(Passenger passenger, PassengerRequest request) {
        passenger.setLastName(request.lastName());
        passenger.setFirstName(request.firstName());
        passenger.setMiddleName(request.middleName());
        passenger.setBirthDate(request.birthDate());
        passenger.setDocument(request.documentType(), request.documentNumber());
        passenger.setEmail(request.email());
        passenger.setPhone(request.phone());
    }
}
