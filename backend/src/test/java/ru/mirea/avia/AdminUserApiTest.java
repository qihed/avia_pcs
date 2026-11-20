package ru.mirea.avia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.domain.UserAccount;
import ru.mirea.avia.repository.UserAccountRepository;
import ru.mirea.avia.security.AuthUser;
import ru.mirea.avia.security.PasswordPolicy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Администрирование пользователей: права, поиск, создание, изменение, защита от потери доступа. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUserApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordPolicy policy;

    /** Уникальный префикс: H2 общая для всех тестов, а AuthFlowTest оставляет своих пользователей. */
    String prefix;
    UserAccount admin;

    @BeforeEach
    void fixture() {
        prefix = "t" + UUID.randomUUID().toString().substring(0, 6);
        admin = save(prefix + "-admin", "Админ Главный", Role.ADMIN, true);
        save(prefix + "-ivanov", "Иванов Иван", Role.EDITOR, true);
        save(prefix + "-petrov", "Петров Пётр", Role.VIEWER, true);
        save(prefix + "-sidorov", "Сидоров Иван", Role.VIEWER, false);
    }

    UserAccount save(String login, String name, Role role, boolean enabled) {
        UserAccount user = new UserAccount(login, name, encoder.encode("Str0ng!Passw0rd"), Set.of(role));
        user.setEnabled(enabled);
        return users.saveAndFlush(user);
    }

    RequestPostProcessor as(UserAccount user) {
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .flatMap(r -> r.withIncluded().stream())
                .distinct()
                .map(r -> new SimpleGrantedAuthority(r.authority()))
                .toList();
        AuthUser principal = new AuthUser(user.getId(), user.getLogin(), user.getDisplayName(), false, user.getRoles());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    String body(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    @Test
    void onlyAdminCanManageUsers() throws Exception {
        UserAccount editor = users.findByLoginIgnoreCase(prefix + "-ivanov").orElseThrow();
        mvc.perform(get("/api/admin/users").with(as(editor))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").with(as(admin))).andExpect(status().isOk());
    }

    @Test
    void searchFiltersSortAndPaging() throws Exception {
        // поиск по части имени без учёта регистра, только «свои» записи через префикс логина
        mvc.perform(get("/api/admin/users").with(as(admin))
                        .param("q", prefix + " иван").param("sort", "login,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].login").value(prefix + "-ivanov"))
                .andExpect(jsonPath("$.content[1].login").value(prefix + "-sidorov"));

        mvc.perform(get("/api/admin/users").with(as(admin))
                        .param("q", prefix).param("role", "VIEWER").param("enabled", "true"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].login").value(prefix + "-petrov"));

        mvc.perform(get("/api/admin/users").with(as(admin))
                        .param("q", prefix).param("sort", "login,desc").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.content[0].login").value(prefix + "-ivanov"))
                .andExpect(jsonPath("$.content[1].login").value(prefix + "-admin"));

        // символ % — обычный символ, а не шаблон LIKE
        mvc.perform(get("/api/admin/users").with(as(admin)).param("q", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsBadListParameters() throws Exception {
        mvc.perform(get("/api/admin/users").with(as(admin)).param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SORT"));
        mvc.perform(get("/api/admin/users").with(as(admin)).param("size", "1000"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
        mvc.perform(get("/api/admin/users").with(as(admin)).param("role", "ROOT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void createReturnsTemporaryPasswordOnce() throws Exception {
        String login = (prefix + "-New.User@Mail.ru");
        JsonNode created = json(mvc.perform(post("/api/admin/users").with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("login", login, "displayName", "  Новый  ", "roles", List.of("EDITOR")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.login").value(login.toLowerCase()))
                .andExpect(jsonPath("$.user.displayName").value("Новый"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()));

        String temporary = created.get("temporaryPassword").asText();
        assertThat(policy.violations(temporary, login)).isEmpty();
        UserAccount saved = users.findByLoginIgnoreCase(login).orElseThrow();
        assertThat(encoder.matches(temporary, saved.getPasswordHash())).isTrue();

        // тот же логин в другом регистре — конфликт
        mvc.perform(post("/api/admin/users").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("login", login.toUpperCase(), "displayName", "Дубль", "roles", List.of("VIEWER")))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOGIN_TAKEN"));

        // ошибки полей приходят списком fieldErrors
        mvc.perform(post("/api/admin/users").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("login", "a b", "displayName", " ", "roles", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void updateChecksVersionAndRevokesAccess() throws Exception {
        UserAccount editor = users.findByLoginIgnoreCase(prefix + "-ivanov").orElseThrow();
        String url = "/api/admin/users/" + editor.getId();
        long version = editor.getVersion();

        mvc.perform(put(url).with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("displayName", "Иванов И.", "roles", List.of("VIEWER"),
                                "enabled", false, "version", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Иванов И."))
                .andExpect(jsonPath("$.roles[0]").value("VIEWER"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.version").value(version + 1));

        // устаревшая версия — 409, данные не меняются
        mvc.perform(put(url).with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("displayName", "Чужая правка", "roles", List.of("ADMIN"),
                                "enabled", true, "version", version))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(users.findById(editor.getId()).orElseThrow().getDisplayName()).isEqualTo("Иванов И.");
    }

    @Test
    void adminCannotLockThemselvesOutOrRemoveLastAdmin() throws Exception {
        mvc.perform(put("/api/admin/users/" + admin.getId()).with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("displayName", "Я", "roles", List.of("EDITOR"),
                                "enabled", true, "version", admin.getVersion()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));

        // оставляем единственного включённого администратора — другого админа, чем тот, кто действует
        UserAccount second = save(prefix + "-admin2", "Второй админ", Role.ADMIN, true);
        users.findAll().stream()
                .filter(u -> u.getRoles().contains(Role.ADMIN) && !u.getId().equals(admin.getId()) && !u.getId().equals(second.getId()))
                .forEach(u -> u.setEnabled(false));
        users.flush();

        // первый админ отключает второго — можно, сам первый остаётся
        mvc.perform(put("/api/admin/users/" + second.getId()).with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("displayName", "Второй админ", "roles", List.of("ADMIN"),
                                "enabled", false, "version", second.getVersion()))))
                .andExpect(status().isOk());

        // теперь второй (уже отключённый, но с токеном в тесте) пытается снять роль с последнего — запрет
        UserAccount fresh = users.findById(admin.getId()).orElseThrow();
        mvc.perform(put("/api/admin/users/" + admin.getId()).with(as(second)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("displayName", "Админ Главный", "roles", List.of("VIEWER"),
                                "enabled", true, "version", fresh.getVersion()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    void resetPasswordAndUnlock() throws Exception {
        UserAccount viewer = users.findByLoginIgnoreCase(prefix + "-petrov").orElseThrow();
        viewer.registerFailedAttempt(1, 15, Instant.now());
        users.saveAndFlush(viewer);

        mvc.perform(get("/api/admin/users/" + viewer.getId()).with(as(admin)))
                .andExpect(jsonPath("$.locked").value(true));
        mvc.perform(post("/api/admin/users/" + viewer.getId() + "/unlock").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.failedAttempts").value(0));

        JsonNode reset = json(mvc.perform(post("/api/admin/users/" + viewer.getId() + "/reset-password").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true)));
        String temporary = reset.get("temporaryPassword").asText();
        assertThat(encoder.matches(temporary, users.findById(viewer.getId()).orElseThrow().getPasswordHash())).isTrue();

        mvc.perform(post("/api/admin/users/" + admin.getId() + "/reset-password").with(as(admin)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_RESET"));
        mvc.perform(post("/api/admin/users/" + UUID.randomUUID() + "/unlock").with(as(admin)))
                .andExpect(status().isNotFound());
    }
}
