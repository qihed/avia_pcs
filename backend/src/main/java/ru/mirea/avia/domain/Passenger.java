package ru.mirea.avia.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "passengers")
public class Passenger {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @Column(nullable = false, length = 60)
  public String lastName;

  @Column(nullable = false, length = 60)
  public String firstName;

  @Column(length = 60)
  public String middleName;

  @Column(nullable = false)
  public LocalDate birthDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  public DocumentType documentType;

  @Column(nullable = false, length = 20)
  public String documentNumber;

  @Column(nullable = false, length = 120, unique = true)
  public String email;

  @Column(nullable = false, length = 20)
  public String phone;

  @Column(nullable = false)
  public LocalDateTime createdAt;
}
