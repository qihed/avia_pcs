package ru.mirea.avia.dto;

import jakarta.validation.constraints.*;
import ru.mirea.avia.domain.BookingStatus;

public record StatusRequest(
  @NotNull BookingStatus status,
  @NotNull @PositiveOrZero Long version
) {}
