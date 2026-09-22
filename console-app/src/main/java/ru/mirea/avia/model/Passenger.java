package ru.mirea.avia.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

/** Пассажир — участник предметной области. */
public class Passenger {
    private long id;
    private String lastName;
    private String firstName;
    private String middleName;
    private LocalDate birthDate;
    private DocumentType documentType;
    private String documentNumber;
    private String email;
    private String phone;
    private LocalDateTime createdAt;

    public Passenger(long id, String lastName, String firstName, String middleName, LocalDate birthDate,
                     DocumentType documentType, String documentNumber, String email, String phone,
                     LocalDateTime createdAt) {
        this.id = id;
        this.lastName = lastName;
        this.firstName = firstName;
        this.middleName = middleName;
        this.birthDate = birthDate;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.email = email;
        this.phone = phone;
        this.createdAt = createdAt;
    }

    public Passenger(String lastName, String firstName, String middleName, LocalDate birthDate,
                     DocumentType documentType, String documentNumber, String email, String phone) {
        this(0, lastName, firstName, middleName, birthDate, documentType, documentNumber, email, phone, null);
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

    public int getAge() {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return id + " | " + getFullName() + " | " + documentType.getTitle() + " " + documentNumber
                + " | " + email + " | " + phone;
    }
}
