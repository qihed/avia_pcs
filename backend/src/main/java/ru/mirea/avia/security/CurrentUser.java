package ru.mirea.avia.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.mirea.avia.error.UnauthorizedException;

import java.util.Optional;

/** Доступ к текущему пользователю из сервисов: заметки, аудит, проверка прав. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthUser require() {
        return find().orElseThrow(() -> new UnauthorizedException("Требуется авторизация"));
    }
}
