package ru.mirea.avia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Укажите логин") @Size(max = 120, message = "Не длиннее 120 символов") String login,
        @NotBlank(message = "Укажите пароль") @Size(max = 128, message = "Не длиннее 128 символов") String password) {
}
