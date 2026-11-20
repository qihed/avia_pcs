package ru.mirea.avia.service;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.domain.UserAccount;
import ru.mirea.avia.dto.AdminUserDtos.AdminUserResponse;
import ru.mirea.avia.dto.AdminUserDtos.CreateUserRequest;
import ru.mirea.avia.dto.AdminUserDtos.UpdateUserRequest;
import ru.mirea.avia.dto.AdminUserDtos.UserWithPasswordResponse;
import ru.mirea.avia.dto.PageResponse;
import ru.mirea.avia.error.ConflictException;
import ru.mirea.avia.error.NotFoundException;
import ru.mirea.avia.filter.UserFilter;
import ru.mirea.avia.filter.UserSpecifications;
import ru.mirea.avia.repository.RefreshSessionRepository;
import ru.mirea.avia.repository.UserAccountRepository;
import ru.mirea.avia.security.CurrentUser;
import ru.mirea.avia.security.TemporaryPasswordGenerator;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Администрирование учётных записей: список с поиском, создание, изменение ролей и статуса,
 * сброс пароля, снятие блокировки. Пароль задаёт не администратор, а генератор: временный пароль
 * показывается один раз, при первом входе пользователь обязан его сменить.
 * <p>
 * Защита от потери доступа: администратор не может отключить себя или снять с себя роль ADMIN,
 * и в системе всегда остаётся хотя бы один включённый администратор.
 */
@Service
@Transactional
public class UserAdminService {

    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final TemporaryPasswordGenerator passwords;
    private final Clock clock;

    public UserAdminService(UserAccountRepository users, RefreshSessionRepository sessions, PasswordEncoder encoder,
                            TemporaryPasswordGenerator passwords, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(UserFilter filter, Pageable pageable) {
        Instant now = clock.instant();
        return PageResponse.from(users.findAll(UserSpecifications.from(filter), pageable)
                .map(u -> AdminUserResponse.of(u, now)));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID id) {
        return AdminUserResponse.of(require(id), clock.instant());
    }

    public UserWithPasswordResponse create(CreateUserRequest request) {
        String login = request.login().trim().toLowerCase(Locale.ROOT);
        if (users.existsByLoginIgnoreCase(login)) {
            throw new ConflictException("LOGIN_TAKEN", "Пользователь с логином «" + login + "» уже существует");
        }
        String temporary = passwords.generate();
        UserAccount user = new UserAccount(login, request.displayName().trim(), encoder.encode(temporary),
                EnumSet.copyOf(request.roles()));
        users.saveAndFlush(user);
        return new UserWithPasswordResponse(AdminUserResponse.of(user, clock.instant()), temporary);
    }

    public AdminUserResponse update(UUID id, UpdateUserRequest request) {
        Instant now = clock.instant();
        UserAccount user = require(id);
        checkVersion(user, request.version());
        Set<Role> roles = EnumSet.copyOf(request.roles());
        boolean losesAdmin = isActiveAdmin(user) && (!request.enabled() || !roles.contains(Role.ADMIN));
        if (losesAdmin && user.getId().equals(CurrentUser.require().id())) {
            throw new ConflictException("SELF_LOCKOUT",
                    "Нельзя отключить себя или снять с себя роль ADMIN — это сделает другой администратор");
        }
        if (losesAdmin && users.countEnabledWithRoleExcept(Role.ADMIN, user.getId()) == 0) {
            throw new ConflictException("LAST_ADMIN", "Это последний активный администратор — сначала назначьте другого");
        }
        boolean disabling = user.isEnabled() && !request.enabled();
        user.setDisplayName(request.displayName().trim());
        user.setRoles(roles);
        user.setEnabled(request.enabled());
        if (disabling) {
            // отключённый пользователь не сможет обновить токен; текущий access-токен отсекает JwtAuthenticationFilter
            sessions.revokeAllForUser(user.getId(), now);
        }
        users.saveAndFlush(user);
        return AdminUserResponse.of(user, now);
    }

    /** Новый временный пароль: все сессии пользователя закрываются, при входе пароль придётся сменить. */
    public UserWithPasswordResponse resetPassword(UUID id) {
        Instant now = clock.instant();
        UserAccount user = require(id);
        if (user.getId().equals(CurrentUser.require().id())) {
            throw new ConflictException("SELF_RESET",
                    "Свой пароль меняйте через «Смену пароля», а не через сброс");
        }
        String temporary = passwords.generate();
        user.changePassword(encoder.encode(temporary), true, now);
        sessions.revokeAllForUser(user.getId(), now);
        users.saveAndFlush(user);
        return new UserWithPasswordResponse(AdminUserResponse.of(user, now), temporary);
    }

    /** Снять блокировку входа после серии неверных паролей. */
    public AdminUserResponse unlock(UUID id) {
        Instant now = clock.instant();
        UserAccount user = require(id);
        user.unlock(now);
        users.saveAndFlush(user);
        return AdminUserResponse.of(user, now);
    }

    private boolean isActiveAdmin(UserAccount user) {
        return user.isEnabled() && user.getRoles().contains(Role.ADMIN);
    }

    private void checkVersion(UserAccount user, Long version) {
        if (version == null || user.getVersion() != version) {
            throw new ConflictException("VERSION_CONFLICT",
                    "Пользователь изменён другим администратором. Обновите данные и повторите");
        }
    }

    private UserAccount require(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }
}
