package br.com.fintech.user.domain;

import java.util.Locale;

/** Forma única do e-mail: a que é gravada, buscada e comparada (unicidade no banco). */
public final class EmailAddress {

    private EmailAddress() {
    }

    /** Sem espaços nas pontas e em minúsculas, para {@code Maria@X.com} e {@code maria@x.com} serem a mesma conta. */
    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
