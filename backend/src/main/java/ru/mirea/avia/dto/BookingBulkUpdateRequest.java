package ru.mirea.avia.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record BookingBulkUpdateRequest(
        @NotEmpty List<UUID> bookingIds,
        @Pattern(regexp = "CREATED|PAID|CHECKED_IN|COMPLETED|CANCELLED|EXPIRED") String status,
        Boolean archived) {}
