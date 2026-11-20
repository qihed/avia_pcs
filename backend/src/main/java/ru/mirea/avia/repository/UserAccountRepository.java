package ru.mirea.avia.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.domain.UserAccount;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    /** Сколько включённых пользователей с ролью role, кроме указанного (нужно, чтобы не остаться без админа). */
    @Query("select count(u) from UserAccount u where u.enabled = true and :role member of u.roles and u.id <> :exceptId")
    long countEnabledWithRoleExcept(@Param("role") Role role, @Param("exceptId") UUID exceptId);
}
