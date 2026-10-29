package br.com.fintech.auth.domain;

/** Finalidade de um token enviado por e-mail. Valor novo exige migration (CHECK em {@code email_tokens}). */
public enum EmailTokenPurpose {
    EMAIL_VERIFICATION,
    EMAIL_CHANGE,
    PASSWORD_RESET
}
