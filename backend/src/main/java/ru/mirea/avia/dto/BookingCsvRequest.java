package ru.mirea.avia.dto;

import jakarta.validation.constraints.NotBlank;

public record BookingCsvRequest(@NotBlank String csv) {}
