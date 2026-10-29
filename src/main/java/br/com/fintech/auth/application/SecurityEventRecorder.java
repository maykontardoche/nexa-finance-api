package br.com.fintech.auth.application;

import br.com.fintech.auth.domain.SecurityEvent;
import br.com.fintech.auth.domain.SecurityEventRepository;
import br.com.fintech.auth.domain.SecurityEventType;
import br.com.fintech.shared.web.RequestOrigin;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava a atividade de segurança da conta. Roda na transação do caso de uso: o evento só existe
 * se a operação que ele descreve foi gravada.
 */
@Component
public class SecurityEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventRecorder.class);

    private final SecurityEventRepository events;
    private final Clock clock;

    public SecurityEventRecorder(SecurityEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID userId, SecurityEventType type, RequestOrigin origin) {
        record(userId, type, origin, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID userId, SecurityEventType type, RequestOrigin origin, @Nullable UUID sessionId) {
        events.save(new SecurityEvent(userId, type, clock.instant(), origin.device(), origin.ip(), sessionId));
        // Só o id: e-mail, nome e origem não vão para o log.
        log.info("Evento de segurança {} userId={}", type, userId);
    }
}
