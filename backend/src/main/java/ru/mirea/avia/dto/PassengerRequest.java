package ru.mirea.avia.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import ru.mirea.avia.domain.DocumentType;

public record PassengerRequest(
  @NotBlank @Size(max = 60) String lastName,
  @NotBlank @Size(max = 60) String firstName,
  @Size(max = 60) String middleName,
  @NotNull @Past LocalDate birthDate,
  @NotNull DocumentType documentType,
  @NotBlank @Size(max = 20) String documentNumber,
  @NotBlank @Email @Size(max = 120) String email,
  @NotBlank @Pattern(regexp = "[+0-9 ()-]{5,20}") String phone
) {}
