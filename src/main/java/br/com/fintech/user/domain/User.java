package br.com.fintech.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * Conta de um usuário. O e-mail chega já normalizado e a senha, só como hash: a entidade nunca
 * vê a senha em claro.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private @Nullable UUID id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "email_verified_at")
    private @Nullable Instant emailVerifiedAt;

    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    @Column(name = "locked_until")
    private @Nullable Instant lockedUntil;

    @Column(name = "last_login_at")
    private @Nullable Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Além do lock otimista, é a versão nula que faz o Spring Data tratar a entidade como nova.
    @Version
    private @Nullable Long version;

    public User(String fullName, String email, String passwordHash, Instant now) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Só para o JPA, que preenche os campos por reflexão. */
    protected User() {
        this.fullName = "";
        this.email = "";
        this.passwordHash = "";
        this.createdAt = Instant.EPOCH;
        this.updatedAt = Instant.EPOCH;
    }

    public @Nullable UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public @Nullable Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public int getFailedLogins() {
        return failedLogins;
    }

    public @Nullable Instant getLockedUntil() {
        return lockedUntil;
    }

    public @Nullable Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
