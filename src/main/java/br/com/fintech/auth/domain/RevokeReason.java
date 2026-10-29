package br.com.fintech.auth.domain;

/** Por que uma sessão foi encerrada. Valor novo exige migration (CHECK em {@code sessions}). */
public enum RevokeReason {
    LOGOUT,
    REVOKED_BY_USER,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    REFRESH_REUSE
}
