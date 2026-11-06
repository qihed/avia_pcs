package ru.mirea.avia;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.domain.UserAccount;
import ru.mirea.avia.repository.UserAccountRepository;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Сквозные сценарии авторизации через MockMvc. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder encoder;

    private String createUser(Role role, boolean enabled, boolean temporary) {
        String login = "u" + UUID.randomUUID().toString().substring(0, 8) + "@test.org";
        UserAccount user = new UserAccount(login, "Test " + role, encoder.encode(PASSWORD), Set.of(role));
        user.setEnabled(enabled);
        user.changePassword(user.getPasswordHash(), temporary, java.time.Instant.now());
        users.save(user);
        return login;
    }

    private ResultActions login(String login, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private String field(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    private String accessToken(Role role) throws Exception {
        String login = createUser(role, true, false);
        return field(login(login, PASSWORD).andExpect(status().isOk()), "$.accessToken");
    }

    @Test
    void loginReturnsTokensAndMe() throws Exception {
        String login = createUser(Role.VIEWER, true, false);
        ResultActions result = login(login, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.roles[0]").value("VIEWER"));
        String token = field(result, "$.accessToken");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value(login));
    }

    @Test
    void wrongPasswordIsUnauthorizedWithUnifiedError() throws Exception {
        String login = createUser(Role.VIEWER, true, false);
        login(login, "Wrong!Passw0rd1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.trace").doesNotExist());
        login("nobody@test.org", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void validationErrorsAreBadRequest() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accountIsLockedAfterRepeatedFailures() throws Exception {
        String login = createUser(Role.VIEWER, true, false);
        for (int i = 0; i < 3; i++) {
            login(login, "Wrong!Passw0rd1").andExpect(status().isUnauthorized());
        }
        login(login, PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void disabledAccountCannotLogin() throws Exception {
        String login = createUser(Role.VIEWER, false, false);
        login(login, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void refreshRotatesTokenAndReuseIsRejected() throws Exception {
        String login = createUser(Role.EDITOR, true, false);
        String refresh1 = field(login(login, PASSWORD).andExpect(status().isOk()), "$.refreshToken");

        ResultActions second = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh1 + "\"}")).andExpect(status().isOk());
        String refresh2 = field(second, "$.refreshToken");

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh1 + "\"}")).andExpect(status().isUnauthorized());
        // повторное использование старого токена отзывает и новую цепочку
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh2 + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshSession() throws Exception {
        String login = createUser(Role.VIEWER, true, false);
        String refresh = field(login(login, PASSWORD).andExpect(status().isOk()), "$.refreshToken");
        String body = "{\"refreshToken\":\"" + refresh + "\"}";

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeUsingApi() throws Exception {
        String login = createUser(Role.ADMIN, true, true);
        ResultActions result = login(login, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        String token = field(result, "$.accessToken");

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"N3w!Passw0rdXyz\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));

        ResultActions changed = mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"N3w!Passw0rdXyz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        String newToken = field(changed, "$.accessToken");

        // эндпоинта пока нет: 404 означает, что защита пропустила запрос
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isNotFound());
        login(login, PASSWORD).andExpect(status().isUnauthorized());
        login(login, "N3w!Passw0rdXyz").andExpect(status().isOk());
    }

    @Test
    void rolesRestrictAccess() throws Exception {
        String viewer = accessToken(Role.VIEWER);
        String editor = accessToken(Role.EDITOR);
        String admin = accessToken(Role.ADMIN);

        mvc.perform(get("/api/items")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/items").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());

        // чтение: любой авторизованный; 404 = защита пройдена, контроллера ещё нет
        mvc.perform(get("/api/items").header("Authorization", "Bearer " + viewer)).andExpect(status().isNotFound());
        // изменение: EDITOR и выше
        mvc.perform(post("/api/items").header("Authorization", "Bearer " + viewer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/items").header("Authorization", "Bearer " + editor)).andExpect(status().isNotFound());
        // окончательное удаление, справочники, админка: только ADMIN
        String id = UUID.randomUUID().toString();
        mvc.perform(delete("/api/items/" + id + "/permanent").header("Authorization", "Bearer " + editor))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/items/" + id + "/permanent").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/references").header("Authorization", "Bearer " + editor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit").header("Authorization", "Bearer " + editor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit").header("Authorization", "Bearer " + admin)).andExpect(status().isNotFound());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
