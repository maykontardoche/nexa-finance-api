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
 * Token de uso único enviado por e-mail (confirmação, troca de e-mail, redefinição de senha).
 * Só o hash fica no banco; o token em si existe apenas no e-mail.
 */
@Entity
@Table(name = "email_tokens")
public class EmailToken {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private @Nullable UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private EmailTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    /** Só na troca de e-mail: o endereço que passa a valer depois da confirmação. */
    @Column(name = "new_email", length = 254, updatable = false)
    private @Nullable String newEmail;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private @Nullable Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public EmailToken(UUID userId, EmailTokenPurpose purpose, String tokenHash, @Nullable String newEmail,
            Instant now, Instant expiresAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.newEmail = newEmail;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    /** Token novo, válido pelo prazo da finalidade a partir de {@code now}. */
    public static EmailToken issue(UUID userId, EmailTokenPurpose purpose, String tokenHash,
            @Nullable String newEmail, Instant now) {
        return new EmailToken(userId, purpose, tokenHash, newEmail, now, now.plus(purpose.validity()));
    }

    /** Só para o JPA, que preenche os campos por reflexão. */
    protected EmailToken() {
        this.userId = new UUID(0, 0);
        this.purpose = EmailTokenPurpose.EMAIL_VERIFICATION;
        this.tokenHash = "";
        this.createdAt = Instant.EPOCH;
        this.expiresAt = Instant.EPOCH;
    }

    public @Nullable UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public EmailTokenPurpose getPurpose() {
        return purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public @Nullable String getNewEmail() {
        return newEmail;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public @Nullable Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Ainda não usado e dentro do prazo. O instante exato da expiração já não vale. O consumo
     * no banco repete esta regra de forma atômica (ver {@code EmailTokenRepository#consume}).
     */
    public boolean isUsableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
