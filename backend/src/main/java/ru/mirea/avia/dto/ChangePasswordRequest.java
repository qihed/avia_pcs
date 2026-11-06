package ru.mirea.avia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Укажите текущий пароль") @Size(max = 128, message = "Не длиннее 128 символов") String currentPassword,
        @NotBlank(message = "Укажите новый пароль") @Size(max = 128, message = "Не длиннее 128 символов") String newPassword) {
}
