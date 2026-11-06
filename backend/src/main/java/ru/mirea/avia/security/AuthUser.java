package ru.mirea.avia.security;

import ru.mirea.avia.domain.Role;

import java.util.Set;
import java.util.UUID;

/** Аутентифицированный пользователь текущего запроса (principal). */
public record AuthUser(UUID id, String login, String displayName, boolean mustChangePassword, Set<Role> roles) {

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
