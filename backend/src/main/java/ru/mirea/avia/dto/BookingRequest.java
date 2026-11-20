package ru.mirea.avia.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import ru.mirea.avia.domain.FareClass;

public record BookingRequest(
  @NotNull UUID passengerId,
  @NotNull UUID flightId,
  @NotBlank @Pattern(regexp = "[1-9][0-9]?[A-F]") String seatNumber,
  @NotNull FareClass fareClass,
  @NotNull
  @DecimalMin("0.01")
  @Digits(integer = 8, fraction = 2)
  BigDecimal price,
  boolean extraBaggage,
  @PositiveOrZero Long version
) {}
