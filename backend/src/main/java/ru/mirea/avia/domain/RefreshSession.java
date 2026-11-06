package ru.mirea.avia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Серверная refresh-сессия. Сам токен не хранится — только его SHA-256. */
@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent", length = 200)
    private String userAgent;

    protected RefreshSession() {
    }

    public RefreshSession(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt, String userAgent) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 200));
    }

    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
