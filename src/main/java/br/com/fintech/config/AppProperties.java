package br.com.fintech.config;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração própria da aplicação ({@code app.*}).
 *
 * @param baseUrl endereço em que o usuário acessa a API; base dos links enviados por e-mail
 * @param mail remetente dos e-mails
 */
@ConfigurationProperties("app")
public record AppProperties(URI baseUrl, Mail mail) {

    public static final String DEFAULT_FROM = "Nexa Finance <nao-responda@nexa-finance.local>";

    public AppProperties {
        if (!isPlainHttpBase(baseUrl)) {
            // Só o nome da variável: o valor pode ter sido digitado errado com algo sensível.
            throw new IllegalArgumentException(
                    "APP_BASE_URL precisa ser um endereço http(s) absoluto, sem query nem fragmento");
        }
        mail = mail == null ? new Mail(null) : mail;
    }

    private static boolean isPlainHttpBase(URI uri) {
        boolean http = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        return http && uri.getHost() != null && uri.getRawQuery() == null && uri.getRawFragment() == null;
    }

    /** @param from remetente; vazio usa {@link #DEFAULT_FROM} */
    public record Mail(@Nullable String from) {

        public Mail {
            from = from == null || from.isBlank() ? DEFAULT_FROM : from.strip();
        }
    }
}
