package ru.mirea.avia.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.domain.UserAccount;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    /** Единый порядок блокировок: параллельные администраторы не смогут отключить друг друга одновременно. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where :role member of u.roles order by u.id")
    List<UserAccount> lockAdministrators(@Param("role") Role role);

    /** Сколько включённых пользователей с ролью role, кроме указанного (нужно, чтобы не остаться без админа). */
    @Query("select count(u) from UserAccount u where u.enabled = true and :role member of u.roles and u.id <> :exceptId")
    long countEnabledWithRoleExcept(@Param("role") Role role, @Param("exceptId") UUID exceptId);
}
