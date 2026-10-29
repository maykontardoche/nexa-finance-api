package br.com.fintech.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fintech.config.AppProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;

class MailLinksTest {

    @Test
    void buildsAnAbsoluteLinkFromTheBaseUrl() {
        assertThat(links("http://localhost:8080").buildLink("/api/v1/auth/email-verification", "token", "AbC-_123"))
                .isEqualTo("http://localhost:8080/api/v1/auth/email-verification?token=AbC-_123");
    }

    @Test
    void keepsThePathOfTheBaseUrlWithoutDoubleSlashes() {
        assertThat(links("https://exemplo.com/nexa/").buildLink("/api/v1/x", "token", "t"))
                .isEqualTo("https://exemplo.com/nexa/api/v1/x?token=t");
    }

    @Test
    void encodesTheWholeQueryValue() {
        assertThat(links("http://localhost:8080").buildLink("/api/v1/x", "token", "a b&c=d/é+{x}"))
                .isEqualTo("http://localhost:8080/api/v1/x?token=a%20b%26c%3Dd%2F%C3%A9%2B%7Bx%7D");
    }

    private static MailLinks links(String baseUrl) {
        return new MailLinks(new AppProperties(URI.create(baseUrl), null));
    }
}
