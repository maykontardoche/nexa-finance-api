package br.com.fintech.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import br.com.fintech.TestcontainersConfiguration;
import br.com.fintech.config.AppProperties;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** Envio de e-mail contra um SMTP real embutido (GreenMail) e o Postgres do Testcontainers. */
@SpringBootTest(properties = {"spring.mail.host=localhost", "spring.mail.port=3025"})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class MailNotifierIntegrationTests {

    private static final String RECIPIENT = "maria.destinataria@example.com";
    private static final Duration DELIVERY = Duration.ofSeconds(10);

    @RegisterExtension
    static final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    MailNotifier notifier;

    @Autowired
    MailLinks links;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ObservationRegistry observations;

    @Autowired
    Tracer tracer;

    @Test
    void committedTransactionDeliversPortugueseTextWithTheLinkWrittenOut() throws Exception {
        String link = links.buildLink("/api/v1/auth/email-verification", "token", "AbC-_123");
        String body = "Olá, Maria!\n\nConfirme seu e-mail abrindo o endereço abaixo:\n\n" + link
                + "\n\nSe não foi você, ignore esta mensagem.";

        transaction.executeWithoutResult(status ->
                notifier.sendAfterCommit(new MailMessage("teste", RECIPIENT, "Confirme seu e-mail ✓", body)));

        assertThat(greenMail.waitForIncomingEmail(DELIVERY.toMillis(), 1)).isTrue();
        MimeMessage received = greenMail.getReceivedMessages()[0];
        assertThat(received.getAllRecipients()).containsExactly(new InternetAddress(RECIPIENT));
        assertThat(((InternetAddress) received.getFrom()[0]).toUnicodeString()).isEqualTo(AppProperties.DEFAULT_FROM);
        assertThat(received.getSubject()).isEqualTo("Confirme seu e-mail ✓");
        assertThat(received.getContentType()).startsWith("text/plain").containsIgnoringCase("charset=UTF-8");
        assertThat(((String) received.getContent()).replace("\r\n", "\n")).isEqualTo(body);
        assertThat(link).isEqualTo("http://localhost:8080/api/v1/auth/email-verification?token=AbC-_123");
    }

    @Test
    void rolledBackTransactionSendsNothing() throws Exception {
        transaction.executeWithoutResult(status -> {
            notifier.sendAfterCommit(new MailMessage("desfeito", RECIPIENT, "Não deveria chegar", "corpo desfeito"));
            status.setRollbackOnly();
        });
        // Marcador: o descarte do desfeito é decidido no rollback, antes de o confirmado ser agendado.
        transaction.executeWithoutResult(status ->
                notifier.sendAfterCommit(new MailMessage("confirmado", RECIPIENT, "Confirmado", "corpo confirmado")));

        assertThat(greenMail.waitForIncomingEmail(DELIVERY.toMillis(), 1)).isTrue();
        assertThat(greenMail.getReceivedMessages()).extracting(MimeMessage::getSubject).containsExactly("Confirmado");
    }

    @Test
    void publishingOutsideATransactionIsRefusedInsteadOfSilentlyDropped() {
        assertThatThrownBy(() -> notifier.sendAfterCommit(new MailMessage("sem-transacao", RECIPIENT, "x", "y")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem-transacao")
                .hasMessageNotContaining(RECIPIENT);
    }

    @Test
    void smtpFailureIsOnlyLoggedWithTheOriginTraceAndKeepsTheBusinessChange(CapturedOutput output) {
        greenMail.stop();
        String token = "token-secreto-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AtomicReference<String> traceId = new AtomicReference<>();

        try {
            sendInsideAnObservedTransaction(userId, token, traceId);

            await().atMost(DELIVERY).untilAsserted(() -> assertThat(output.getAll())
                    .contains("Falha ao enviar e-mail teste-falha: MailSendException (causa "));
            String warning = output.getAll().lines()
                    .filter(line -> line.contains("Falha ao enviar e-mail teste-falha"))
                    .findFirst()
                    .orElseThrow();
            assertThat(warning).contains(" WARN ").contains(traceId.get());
            assertThat(output.getAll()).doesNotContain(RECIPIENT).doesNotContain(token);
            // O envio falhou depois do commit: a mudança de negócio continua lá.
            assertThat(jdbc.sql("SELECT count(*) FROM users WHERE id = :id").param("id", userId)
                            .query(Long.class).single())
                    .isEqualTo(1);
        } finally {
            jdbc.sql("DELETE FROM users WHERE id = :id").param("id", userId).update();
        }
    }

    private void sendInsideAnObservedTransaction(UUID userId, String token, AtomicReference<String> traceId) {
        // Como numa requisição: a observação abre o trace, e o envio assíncrono herda o contexto.
        Observation.createNotStarted("teste-envio", observations).observe(() -> {
            traceId.set(tracer.currentSpan().context().traceId());
            transaction.executeWithoutResult(status -> {
                jdbc.sql("INSERT INTO users (id, full_name, email, password_hash, created_at, updated_at, version)"
                                + " VALUES (:id, 'Maria', :email, 'h', now(), now(), 0)")
                        .param("id", userId)
                        .param("email", "falha-" + userId + "@example.com")
                        .update();
                notifier.sendAfterCommit(new MailMessage("teste-falha", RECIPIENT, "Assunto", "link com " + token));
            });
        });
    }
}
