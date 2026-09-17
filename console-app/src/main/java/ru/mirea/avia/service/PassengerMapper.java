package ru.mirea.avia.service;

import ru.mirea.avia.domain.Passenger;
import ru.mirea.avia.dto.PassengerDtos.PassengerResponse;

import java.time.Clock;
import java.time.LocalDate;

/** Преобразует карточку пассажира в публичный DTO; возраст считается на текущую дату часов. */
public final class PassengerMapper {
    private final Clock clock;

    public PassengerMapper(Clock clock) {
        this.clock = clock;
    }

    /** Возвращает карточку пассажира с ФИО, возрастом и маскированным номером документа. */
    public PassengerResponse toResponse(Passenger passenger) {
        int age = passenger.getAge(LocalDate.now(clock));
        return new PassengerResponse(passenger.getId(), passenger.getLastName(), passenger.getFirstName(),
                passenger.getMiddleName(), passenger.getFullName(), passenger.getShortName(),
                passenger.getBirthDate(), age, passenger.getDocumentType(), passenger.getDocumentNumber(),
                passenger.getMaskedDocument(), passenger.getEmail(), passenger.getPhone(),
                passenger.getCreatedAt());
    }
}
