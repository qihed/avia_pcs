package ru.mirea.avia.dto;

import ru.mirea.avia.domain.Role;
import ru.mirea.avia.domain.UserAccount;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String login,
        String displayName,
        List<Role> roles,
        boolean enabled,
        boolean mustChangePassword,
        Instant createdAt,
        Instant lastLoginAt) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getLogin(), user.getDisplayName(),
                user.getRoles().stream().sorted().toList(), user.isEnabled(), user.isMustChangePassword(),
                user.getCreatedAt(), user.getLastLoginAt());
    }
}
