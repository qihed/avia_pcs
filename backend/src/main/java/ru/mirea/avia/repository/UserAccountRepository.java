package ru.mirea.avia.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.avia.domain.UserAccount;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);
}
