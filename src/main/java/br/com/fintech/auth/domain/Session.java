package br.com.fintech.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * Sessão aberta por um login. Guarda só o hash do segredo da credencial de renovação (ADR-0006).
 * O dono é referenciado pelo id: a exclusão do usuário apaga a sessão em cascata no banco.
 */
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private @Nullable UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "refresh_hash", nullable = false, length = 64)
    private String refreshHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 30)
    private @Nullable RevokeReason revokeReason;

    @Column(length = 120)
    private @Nullable String device;

    @Column(length = 45)
    private @Nullable String origin;

    public Session(UUID userId, String refreshHash, Instant now, Instant expiresAt,
            @Nullable String device, @Nullable String origin) {
        this.userId = userId;
        this.refreshHash = refreshHash;
        this.createdAt = now;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
        this.device = device;
        this.origin = origin;
    }

    /** Só para o JPA, que preenche os campos por reflexão. */
    protected Session() {
        this.userId = new UUID(0, 0);
        this.refreshHash = "";
        this.createdAt = Instant.EPOCH;
        this.lastUsedAt = Instant.EPOCH;
        this.expiresAt = Instant.EPOCH;
    }

    public @Nullable UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRefreshHash() {
        return refreshHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public @Nullable Instant getRevokedAt() {
        return revokedAt;
    }

    public @Nullable RevokeReason getRevokeReason() {
        return revokeReason;
    }

    public @Nullable String getDevice() {
        return device;
    }

    public @Nullable String getOrigin() {
        return origin;
    }
}
