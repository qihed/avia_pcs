package ru.mirea.avia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Тело для POST /api/auth/refresh и POST /api/auth/logout. */
public record RefreshRequest(
        @NotBlank(message = "Укажите refresh-токен") @Size(max = 200, message = "Некорректный токен") String refreshToken) {
}
