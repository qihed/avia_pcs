package ru.mirea.avia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import ru.mirea.avia.controller.AuthController;
import ru.mirea.avia.repository.UserAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** Smoke-тест: Spring-контекст поднимается, создан bootstrap-администратор. */
@SpringBootTest
@ActiveProfiles("test")
class AviaApplicationTests {

    @Autowired
    ApplicationContext context;

    @Autowired
    UserAccountRepository users;

    @Test
    void contextLoads() {
        assertThat(context.getBean(AuthController.class)).isNotNull();
    }

    @Test
    void bootstrapAdminIsCreatedWithTemporaryPassword() {
        var admin = users.findByLoginIgnoreCase("admin@example.org").orElseThrow();
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(admin.getRoles()).extracting(Enum::name).containsExactly("ADMIN");
    }
}
