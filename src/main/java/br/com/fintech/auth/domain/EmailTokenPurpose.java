package br.com.fintech.auth.domain;

import java.time.Duration;

/** Finalidade de um token enviado por e-mail. Valor novo exige migration (CHECK em {@code email_tokens}). */
public enum EmailTokenPurpose {
    EMAIL_VERIFICATION(Duration.ofHours(24)),
    EMAIL_CHANGE(Duration.ofHours(24)),
    PASSWORD_RESET(Duration.ofMinutes(30));

    private final Duration validity;

    EmailTokenPurpose(Duration validity) {
        this.validity = validity;
    }

    /** Por quanto tempo, a partir da emissão, o link do e-mail vale. */
    public Duration validity() {
        return validity;
    }
}
