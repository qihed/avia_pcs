package ru.mirea.avia.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String login;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected UserAccount() {
    }

    public UserAccount(String login, String displayName, String passwordHash, Set<Role> roles) {
        this.login = login;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void registerFailedAttempt(int maxAttempts, long lockMinutes, Instant now) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            lockedUntil = now.plusSeconds(lockMinutes * 60);
            failedAttempts = 0;
        }
        updatedAt = now;
    }

    public void registerSuccessfulLogin(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
        updatedAt = now;
    }

    /** Смена пароля: temporary=true для пароля, выданного администратором (его придётся сменить при входе). */
    public void changePassword(String newHash, boolean temporary, Instant now) {
        this.passwordHash = newHash;
        this.mustChangePassword = temporary;
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getLogin() { return login; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public Set<Role> getRoles() { return roles; }

    public void setLogin(String login) { this.login = login; this.updatedAt = Instant.now(); }
    public void setDisplayName(String displayName) { this.displayName = displayName; this.updatedAt = Instant.now(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; this.updatedAt = Instant.now(); }
    public void setRoles(Set<Role> roles) {
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
        this.updatedAt = Instant.now();
    }
}
