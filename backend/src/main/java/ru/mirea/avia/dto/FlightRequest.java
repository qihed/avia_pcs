package ru.mirea.avia.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import ru.mirea.avia.domain.FlightStatus;

public record FlightRequest(
  @NotBlank @Pattern(regexp = "[A-Z0-9]{2,8}") String flightNumber,
  @NotBlank @Size(max = 60) String airline,
  @NotNull @Pattern(regexp = "[A-Z]{3}") String departureAirport,
  @NotNull @Pattern(regexp = "[A-Z]{3}") String arrivalAirport,
  @NotNull LocalDateTime departureTime,
  @NotNull LocalDateTime arrivalTime,
  @NotBlank @Size(max = 40) String aircraftType,
  @Min(1) @Max(594) int totalSeats,
  @NotNull
  @DecimalMin("0.01")
  @Digits(integer = 8, fraction = 2)
  BigDecimal basePrice,
  @NotNull FlightStatus status,
  boolean active,
  @PositiveOrZero Long version
) {}
