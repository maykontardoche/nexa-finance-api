package br.com.fintech.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AppPropertiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080", "https://nexa.exemplo.com", "http://127.0.0.1:9000/nexa/"})
    void acceptsAbsoluteHttpAddresses(String baseUrl) {
        assertThat(new AppProperties(URI.create(baseUrl), null).baseUrl()).isEqualTo(URI.create(baseUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost:8080", "/api", "ftp://localhost", "http://localhost:8080?x=1",
        "http://localhost:8080#topo", "file:///etc/passwd", "http:///sem-host"})
    void refusesAnythingThatIsNotAPlainHttpBase(String baseUrl) {
        assertThatThrownBy(() -> new AppProperties(URI.create(baseUrl), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APP_BASE_URL")
                .hasMessageNotContaining(baseUrl);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void blankSenderFallsBackToTheDefault(String from) {
        AppProperties properties = new AppProperties(URI.create("http://localhost:8080"), new AppProperties.Mail(from));

        assertThat(properties.mail().from()).isEqualTo(AppProperties.DEFAULT_FROM);
    }

    @ParameterizedTest
    @ValueSource(strings = " Equipe <equipe@exemplo.com> ")
    void customSenderIsKeptWithoutTheSurroundingSpaces(String from) {
        assertThat(new AppProperties.Mail(from).from()).isEqualTo("Equipe <equipe@exemplo.com>");
    }
}
