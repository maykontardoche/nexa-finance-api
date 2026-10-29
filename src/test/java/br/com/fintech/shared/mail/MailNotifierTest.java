package br.com.fintech.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import br.com.fintech.config.AppProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Os dois ramos de falha do envio: nunca propagam e nunca logam a mensagem da exceção. */
@ExtendWith(OutputCaptureExtension.class)
class MailNotifierTest {

    private static final String RECIPIENT = "maria.ramo@example.com";

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final MailNotifier notifier = new MailNotifier(
            mock(ApplicationEventPublisher.class), sender, new AppProperties(URI.create("http://localhost:8080"), null));

    @Test
    void mailFailureIsAWarningWithTypesAndOriginButNotTheMessage(CapturedOutput output) {
        doThrow(new MailSendException("550 caixa inexistente: " + RECIPIENT, new IllegalArgumentException(RECIPIENT)))
                .when(sender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> notifier.deliver(request())).doesNotThrowAnyException();

        assertThat(output.getAll())
                .contains("WARN")
                .contains("Falha ao enviar e-mail ramo: MailSendException (causa java.lang.IllegalArgumentException em ")
                .doesNotContain(RECIPIENT);
    }

    @Test
    void unexpectedFailureIsAnErrorWithTypesAndOriginButNotTheMessage(CapturedOutput output) {
        doThrow(new IllegalStateException("estado inválido para " + RECIPIENT))
                .when(sender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> notifier.deliver(request())).doesNotThrowAnyException();

        assertThat(output.getAll())
                .contains("ERROR")
                .contains("Erro inesperado ao enviar e-mail ramo: IllegalStateException (causa java.lang.IllegalStateException em ")
                .doesNotContain(RECIPIENT);
    }

    @Test
    void messageToStringNeverShowsRecipientNorBody() {
        assertThat(new MailMessage("ramo", RECIPIENT, "Assunto", "corpo com token-123"))
                .hasToString("MailMessage[kind=ramo]");
    }

    private static MailRequested request() {
        return new MailRequested(new MailMessage("ramo", RECIPIENT, "Assunto", "corpo"));
    }
}
