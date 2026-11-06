package ru.mirea.avia.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.avia.config.AppProperties;
import ru.mirea.avia.domain.RefreshSession;
import ru.mirea.avia.domain.UserAccount;
import ru.mirea.avia.dto.TokenResponse;
import ru.mirea.avia.dto.UserResponse;
import ru.mirea.avia.error.ApiException;
import ru.mirea.avia.error.BadRequestException;
import ru.mirea.avia.error.ForbiddenException;
import ru.mirea.avia.error.UnauthorizedException;
import ru.mirea.avia.repository.RefreshSessionRepository;
import ru.mirea.avia.repository.UserAccountRepository;
import ru.mirea.avia.security.JwtService;
import ru.mirea.avia.security.PasswordPolicy;
import ru.mirea.avia.security.TokenHasher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Вход, refresh с ротацией, выход, смена пароля. Исключения {@link ApiException} не откатывают транзакцию:
 * счётчик неудачных попыток и отзыв сессий должны сохраняться даже при отказе.
 */
@Service
@Transactional(noRollbackFor = ApiException.class)
public class AuthService {

    private static final String BAD_CREDENTIALS = "Неверный логин или пароль";

    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwt;
    private final AppProperties props;
    private final Clock clock;
    /** Хеш-заглушка: для несуществующего логина тоже тратим время BCrypt, чтобы нельзя было перебирать логины. */
    private final String dummyHash;

    public AuthService(UserAccountRepository users, RefreshSessionRepository sessions, PasswordEncoder encoder,
                       PasswordPolicy passwordPolicy, JwtService jwt, AppProperties props, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.jwt = jwt;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    public TokenResponse login(String login, String password, String userAgent) {
        Instant now = clock.instant();
        UserAccount user = users.findByLoginIgnoreCase(login.trim()).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash);
            throw new UnauthorizedException("BAD_CREDENTIALS", BAD_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Слишком много неудачных попыток входа. Повторите позже");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            user.registerFailedAttempt(props.security().maxFailedAttempts(), props.security().lockMinutes(), now);
            throw new UnauthorizedException("BAD_CREDENTIALS", BAD_CREDENTIALS);
        }
        if (!user.isEnabled()) {
            throw new ForbiddenException("ACCOUNT_DISABLED", "Учётная запись отключена");
        }
        user.registerSuccessfulLogin(now);
        return issueTokens(user, userAgent, now);
    }

    public TokenResponse refresh(String refreshToken, String userAgent) {
        Instant now = clock.instant();
        RefreshSession session = sessions.findByTokenHash(TokenHasher.sha256Hex(refreshToken))
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Сессия недействительна"));
        if (session.isRevoked()) {
            // повторное использование уже заменённого токена — возможна кража, закрываем все сессии пользователя
            sessions.revokeAllForUser(session.getUserId(), now);
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Сессия недействительна");
        }
        if (session.isExpired(now)) {
            session.revoke(now);
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Сессия истекла, войдите заново");
        }
        UserAccount user = users.findById(session.getUserId()).filter(UserAccount::isEnabled).orElse(null);
        if (user == null) {
            session.revoke(now);
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Сессия недействительна");
        }
        session.revoke(now);
        return issueTokens(user, userAgent, now);
    }

    public void logout(String refreshToken) {
        sessions.findByTokenHash(TokenHasher.sha256Hex(refreshToken))
                .ifPresent(session -> session.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return UserResponse.from(load(userId));
    }

    public TokenResponse changePassword(UUID userId, String currentPassword, String newPassword, String userAgent) {
        Instant now = clock.instant();
        UserAccount user = load(userId);
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("WRONG_CURRENT_PASSWORD", "Текущий пароль указан неверно");
        }
        if (currentPassword.equals(newPassword)) {
            throw new BadRequestException("SAME_PASSWORD", "Новый пароль должен отличаться от текущего");
        }
        passwordPolicy.validate(newPassword, user.getLogin(), "newPassword");
        user.changePassword(encoder.encode(newPassword), false, now);
        sessions.revokeAllForUser(user.getId(), now);
        return issueTokens(user, userAgent, now);
    }

    private UserAccount load(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new UnauthorizedException("Требуется авторизация"));
    }

    private TokenResponse issueTokens(UserAccount user, String userAgent, Instant now) {
        String refreshToken = TokenHasher.newToken();
        Instant expires = now.plus(Duration.ofDays(props.jwt().refreshDays()));
        sessions.save(new RefreshSession(user.getId(), TokenHasher.sha256Hex(refreshToken), now, expires, userAgent));
        return new TokenResponse("Bearer", jwt.createAccessToken(user), jwt.accessTtlSeconds(), refreshToken,
                user.isMustChangePassword(), UserResponse.from(user));
    }
}
