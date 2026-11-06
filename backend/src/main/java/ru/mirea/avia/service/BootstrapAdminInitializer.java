package ru.mirea.avia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.avia.config.AppProperties;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.domain.UserAccount;
import ru.mirea.avia.repository.UserAccountRepository;

import java.util.Set;

/** Создаёт первого администратора из переменных окружения, если такого логина ещё нет. Пароль временный. */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public BootstrapAdminInitializer(UserAccountRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap admin = props.bootstrap();
        if (admin.email() == null || admin.email().isBlank() || admin.password() == null || admin.password().isBlank()) {
            return;
        }
        if (users.existsByLoginIgnoreCase(admin.email())) {
            return;
        }
        UserAccount account = new UserAccount(admin.email().trim(), admin.name(),
                encoder.encode(admin.password()), Set.of(Role.ADMIN));
        users.save(account);
        log.info("Создан администратор {} (временный пароль, потребуется смена при первом входе)", admin.email());
    }
}
