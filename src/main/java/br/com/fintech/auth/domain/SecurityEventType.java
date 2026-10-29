package br.com.fintech.auth.domain;

/** Eventos da atividade de segurança da conta. Valor novo exige migration (CHECK em {@code security_events}). */
public enum SecurityEventType {
    REGISTERED,
    EMAIL_VERIFIED,
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    EMAIL_CHANGE_REQUESTED,
    EMAIL_CHANGED,
    SESSION_REVOKED,
    REFRESH_REUSE
}
