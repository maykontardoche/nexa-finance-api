package br.com.fintech.auth.application;

import br.com.fintech.auth.domain.PasswordViolation;

/** A senha não passou na política; vira {@code validation-error} no campo informado. */
public class PasswordRejectedException extends RuntimeException {

    private final String field;
    private final PasswordViolation violation;

    public PasswordRejectedException(String field, PasswordViolation violation) {
        // Só o motivo: a senha nunca entra na mensagem.
        super("senha recusada: " + violation);
        this.field = field;
        this.violation = violation;
    }

    public String field() {
        return field;
    }

    public PasswordViolation violation() {
        return violation;
    }
}
