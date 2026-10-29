package br.com.fintech.auth.domain;

/** Por que uma senha foi recusada. O texto pt-BR fica no {@code messages_pt_BR.properties}. */
public enum PasswordViolation {
    TOO_SHORT("password.violation.too-short"),
    TOO_LONG("password.violation.too-long"),
    COMMON("password.violation.common"),
    CONTAINS_EMAIL("password.violation.contains-email");

    private final String messageKey;

    PasswordViolation(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }

    /** Argumentos da mensagem: os limites, para o texto não repetir os números da política. */
    public Object[] messageArguments() {
        return switch (this) {
            case TOO_SHORT -> new Object[] {PasswordPolicy.MIN_LENGTH};
            case TOO_LONG -> new Object[] {PasswordPolicy.MAX_LENGTH};
            case COMMON, CONTAINS_EMAIL -> new Object[0];
        };
    }
}
