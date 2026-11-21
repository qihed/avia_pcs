package ru.mirea.avia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.domain.UserAccount;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Запросы и ответы администрирования учётных записей (/api/admin/users). */
public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    /** Логин: латиница, цифры и . _ @ + - (подходит и e-mail). */
    public static final String LOGIN_PATTERN = "^[A-Za-z0-9._@+-]{3,120}$";

    public record CreateUserRequest(
            @NotBlank(message = "Укажите логин")
            @Pattern(regexp = LOGIN_PATTERN, message = "От 3 до 120 символов: латиница, цифры и . _ @ + -")
            String login,
            @NotBlank(message = "Укажите имя")
            @Size(max = 120, message = "Не длиннее 120 символов")
            String displayName,
            @NotEmpty(message = "Выберите роль")
            Set<@NotNull Role> roles) {
    }

    public record UpdateUserRequest(
            @NotBlank(message = "Укажите имя")
            @Size(max = 120, message = "Не длиннее 120 символов")
            String displayName,
            @NotEmpty(message = "Выберите роль")
            Set<@NotNull Role> roles,
            @NotNull(message = "Укажите активность учётной записи")
            Boolean enabled,
            @NotNull(message = "Укажите version")
            @PositiveOrZero
            Long version) {
    }

    public record AdminUserResponse(
            UUID id,
            String login,
            String displayName,
            List<Role> roles,
            boolean enabled,
            boolean mustChangePassword,
            boolean locked,
            Instant lockedUntil,
            int failedAttempts,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        public static AdminUserResponse of(UserAccount user, Instant now) {
            return new AdminUserResponse(user.getId(), user.getLogin(), user.getDisplayName(),
                    user.getRoles().stream().sorted().toList(), user.isEnabled(), user.isMustChangePassword(),
                    user.isLocked(now), user.getLockedUntil(), user.getFailedAttempts(), user.getLastLoginAt(),
                    user.getCreatedAt(), user.getUpdatedAt(), user.getVersion());
        }
    }

    /** Временный пароль показывается один раз: в БД хранится только его хеш. */
    public record UserWithPasswordResponse(AdminUserResponse user, String temporaryPassword) {
    }
}
