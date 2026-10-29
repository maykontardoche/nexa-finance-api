package br.com.fintech.shared.mail;

import br.com.fintech.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Links absolutos dos e-mails, montados sobre {@code APP_BASE_URL}. */
@Component
public class MailLinks {

    private final AppProperties properties;

    public MailLinks(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * Link para um endpoint GET da API com um único parâmetro de query.
     *
     * <p>O valor entra como variável de template para ser codificado por inteiro: com o valor
     * literal, {@code +} passaria cru e o servidor o leria como espaço.
     *
     * @param path caminho a partir da raiz da aplicação (ex.: {@code /api/v1/auth/email-verification})
     */
    public String buildLink(String path, String queryParam, String value) {
        return UriComponentsBuilder.fromUri(properties.baseUrl())
                .path(path)
                .queryParam(queryParam, "{value}")
                .encode()
                .buildAndExpand(value)
                .toUriString();
    }
}
