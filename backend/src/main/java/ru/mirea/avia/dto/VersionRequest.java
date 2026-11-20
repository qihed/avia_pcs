package ru.mirea.avia.dto;

import jakarta.validation.constraints.*;

public record VersionRequest(@NotNull @PositiveOrZero Long version) {}
