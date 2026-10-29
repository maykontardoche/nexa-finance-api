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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/** Registro da atividade de segurança da conta. Só é gravado e apagado, nunca alterado. */
@Entity
@Immutable
@Table(name = "security_events")
public class SecurityEvent {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private @Nullable UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SecurityEventType type;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 120)
    private @Nullable String device;

    @Column(length = 45)
    private @Nullable String origin;

    /** Sessão envolvida, quando houver. Sem FK: o evento sobrevive à limpeza das sessões. */
    @Column(name = "session_id")
    private @Nullable UUID sessionId;

    public SecurityEvent(UUID userId, SecurityEventType type, Instant occurredAt, @Nullable String device,
            @Nullable String origin, @Nullable UUID sessionId) {
        this.userId = userId;
        this.type = type;
        this.occurredAt = occurredAt;
        this.device = device;
        this.origin = origin;
        this.sessionId = sessionId;
    }

    /** Só para o JPA, que preenche os campos por reflexão. */
    protected SecurityEvent() {
        this.userId = new UUID(0, 0);
        this.type = SecurityEventType.REGISTERED;
        this.occurredAt = Instant.EPOCH;
    }

    public @Nullable UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public SecurityEventType getType() {
        return type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public @Nullable String getDevice() {
        return device;
    }

    public @Nullable String getOrigin() {
        return origin;
    }

    public @Nullable UUID getSessionId() {
        return sessionId;
    }
}
