package ru.mirea.avia.dto;

import ru.mirea.avia.domain.DocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO-контракты карточек пассажиров. */
public final class PassengerDtos {
    private PassengerDtos() {
    }

    /** Входная модель создания и изменения карточки. */
    public record PassengerRequest(
            String lastName,
            String firstName,
            String middleName,
            LocalDate birthDate,
            DocumentType documentType,
            String documentNumber,
            String email,
            String phone) {
    }

    /** Выходная карточка пассажира. */
    public record PassengerResponse(
            long id,
            String lastName,
            String firstName,
            String middleName,
            String fullName,
            String shortName,
            LocalDate birthDate,
            int age,
            DocumentType documentType,
            String documentNumber,
            String maskedDocument,
            String email,
            String phone,
            LocalDateTime createdAt) {

        /** Возвращает запрос с текущими значениями карточки — основа диалога изменения. */
        public PassengerRequest toRequest() {
            return new PassengerRequest(lastName, firstName, middleName, birthDate, documentType,
                    documentNumber, email, phone);
        }
    }
}
