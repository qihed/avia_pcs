package ru.mirea.avia.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Objects;

/**
 * Пассажир — участник предметной области.
 *
 * <p>Для одного пассажира может существовать множество броней на разные рейсы, поэтому
 * карточку с бронированиями удалить нельзя (BR-10).</p>
 */
public class Passenger {
    private Long id;
    private String lastName;
    private String firstName;
    private String middleName;
    private LocalDate birthDate;
    private DocumentType documentType;
    private String documentNumber;
    private String email;
    private String phone;
    private LocalDateTime createdAt;

    public Passenger(String lastName, String firstName, LocalDate birthDate, DocumentType documentType,
                     String documentNumber, String email, String phone) {
        this.lastName = Objects.requireNonNull(lastName, "lastName");
        this.firstName = Objects.requireNonNull(firstName, "firstName");
        this.birthDate = Objects.requireNonNull(birthDate, "birthDate");
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.documentNumber = Objects.requireNonNull(documentNumber, "documentNumber");
        this.email = Objects.requireNonNull(email, "email");
        this.phone = Objects.requireNonNull(phone, "phone");
    }

    /** Возвращает фамилию, имя и отчество полностью. */
    public String getFullName() {
        return middleName == null || middleName.isBlank()
                ? lastName + " " + firstName
                : lastName + " " + firstName + " " + middleName;
    }

    /** Возвращает фамилию и инициалы: «Иванов И. И.». */
    public String getShortName() {
        String initials = firstName.charAt(0) + ".";
        if (middleName != null && !middleName.isBlank()) initials += " " + middleName.charAt(0) + ".";
        return lastName + " " + initials;
    }

    public int getAge(LocalDate today) {
        return Period.between(birthDate, today).getYears();
    }

    /** Возвращает маскированный номер документа для списков (NFR-09). */
    public String getMaskedDocument() {
        return documentType.mask(documentNumber);
    }

    /** Меняет тип и номер документа одновременно: номер проверяется по правилу своего типа. */
    public void setDocument(DocumentType documentType, String documentNumber) {
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.documentNumber = Objects.requireNonNull(documentNumber, "documentNumber");
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Passenger passenger && id != null && id.equals(passenger.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Passenger[id=" + id + ", " + getShortName() + ", " + documentType + " " + getMaskedDocument() + "]";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = Objects.requireNonNull(lastName, "lastName"); }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = Objects.requireNonNull(firstName, "firstName"); }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = Objects.requireNonNull(birthDate, "birthDate"); }
    public DocumentType getDocumentType() { return documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = Objects.requireNonNull(email, "email"); }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = Objects.requireNonNull(phone, "phone"); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
