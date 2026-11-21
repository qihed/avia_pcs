package ru.mirea.avia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.domain.UserAccount;
import ru.mirea.avia.repository.UserAccountRepository;
import ru.mirea.avia.security.AuthUser;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserConcurrencyTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserAccountRepository users;

    @Test
    void simultaneousDisablingAlwaysLeavesAnEnabledAdmin() throws Exception {
        List<UUID> originalAdmins = users.findAll().stream()
                .filter(u -> u.isEnabled() && u.getRoles().contains(Role.ADMIN))
                .map(UserAccount::getId).toList();
        for (UUID id : originalAdmins) setEnabled(id, false);
        UserAccount first = users.saveAndFlush(new UserAccount("race-a-" + UUID.randomUUID(), "Первый", "unused", Set.of(Role.ADMIN)));
        UserAccount second = users.saveAndFlush(new UserAccount("race-b-" + UUID.randomUUID(), "Второй", "unused", Set.of(Role.ADMIN)));
        var executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            var one = executor.submit(() -> { start.await(); return disable(first, second); });
            var two = executor.submit(() -> { start.await(); return disable(second, first); });
            start.countDown();
            assertThat(List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(users.findAll().stream().filter(u -> u.isEnabled() && u.getRoles().contains(Role.ADMIN)).count())
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            users.deleteById(first.getId());
            users.deleteById(second.getId());
            for (UUID id : originalAdmins) setEnabled(id, true);
        }
    }

    private void setEnabled(UUID id, boolean enabled) {
        UserAccount user = users.findById(id).orElseThrow();
        user.setEnabled(enabled);
        users.saveAndFlush(user);
    }

    private int disable(UserAccount actor, UserAccount target) throws Exception {
        var principal = new AuthUser(actor.getId(), actor.getLogin(), actor.getDisplayName(), false, actor.getRoles());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return mvc.perform(put("/api/admin/users/" + target.getId()).with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("displayName", target.getDisplayName(),
                                "roles", List.of("ADMIN"), "enabled", false, "version", target.getVersion()))))
                .andReturn().getResponse().getStatus();
    }
}
