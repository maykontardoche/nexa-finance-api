package br.com.fintech.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doReturn;

import br.com.fintech.TestcontainersConfiguration;
import br.com.fintech.shared.web.RequestOrigin;
import br.com.fintech.user.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Dois cadastros simultâneos com o mesmo e-mail: os dois passam pela busca sem achar a conta, e
 * o segundo insert esbarra na unicidade. A resposta tem de ser a mesma de sempre, não um erro.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RegistrationRaceIntegrationTests {

    private static final RequestOrigin ORIGIN = new RequestOrigin("127.0.0.0", null);
    private static final String PASSWORD = "uma frase longa e secreta de cadastro";

    @MockitoSpyBean
    UserRepository users;

    @Autowired
    RegistrationService registration;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void deleteAllUsers() {
        jdbc.sql("DELETE FROM users").update();
    }

    @Test
    void losingTheRaceOnTheUniqueEmailEndsLikeAnyOtherRegistration() {
        registration.register(new RegisterCommand("Maria Silva", "maria@example.com", PASSWORD), ORIGIN);
        // A outra transação ainda não tinha gravado quando esta buscou o e-mail.
        doReturn(Optional.empty()).when(users).findByEmail("maria@example.com");

        assertThatCode(() -> registration.register(
                        new RegisterCommand("Outra Pessoa", "maria@example.com", PASSWORD), ORIGIN))
                .doesNotThrowAnyException();

        assertThat(jdbc.sql("SELECT count(*) FROM users").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT full_name FROM users").query(String.class).single()).isEqualTo("Maria Silva");
        assertThat(jdbc.sql("SELECT count(*) FROM email_tokens").query(Long.class).single()).isEqualTo(1);
    }
}
