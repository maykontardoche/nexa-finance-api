package br.com.fintech.shared.mail;

import br.com.fintech.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envia e-mail só depois do commit e fora da thread da requisição.
 *
 * <p>Depois do commit: operação desfeita não manda e-mail. Assíncrono: o tempo de resposta não
 * depende do SMTP nem revela se houve envio. Falha de envio fica só no log, sem desfazer nada,
 * porque o usuário pode pedir o reenvio.
 */
@Component
public class MailNotifier {

    private static final Logger log = LoggerFactory.getLogger(MailNotifier.class);

    private final ApplicationEventPublisher events;
    private final JavaMailSender sender;
    private final AppProperties properties;

    public MailNotifier(ApplicationEventPublisher events, JavaMailSender sender, AppProperties properties) {
        this.events = events;
        this.sender = sender;
        this.properties = properties;
    }

    /**
     * Agenda o envio para depois do commit da transação atual.
     *
     * @throws IllegalStateException fora de transação, onde o evento seria descartado em silêncio
     */
    public void sendAfterCommit(MailMessage message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("e-mail fora de transação não seria enviado: " + message.kind());
        }
        events.publishEvent(new MailRequested(message));
    }

    // Público para o proxy do @Async; o parâmetro é package-private, então só o Spring o chama.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(MailRequested request) {
        MailMessage message = request.message();
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.mail().from());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        // Nos dois catch, nada da mensagem da exceção: ela pode trazer o endereço do
        // destinatário. Sem eles, o handler padrão do @Async logaria a mensagem inteira.
        try {
            sender.send(mail);
            log.info("E-mail enviado: {}", message.kind());
        } catch (MailException e) {
            log.warn("Falha ao enviar e-mail {}: {}", message.kind(), describe(e));
        } catch (RuntimeException e) {
            log.error("Erro inesperado ao enviar e-mail {}: {}", message.kind(), describe(e));
        }
    }

    /** Tipo da exceção, tipo da causa raiz e onde ela nasceu: diagnóstico sem dado pessoal. */
    static String describe(Throwable failure) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(failure);
        StackTraceElement[] frames = root.getStackTrace();
        String origin = frames.length == 0 ? "?" : frames[0].getClassName() + "." + frames[0].getMethodName()
                + ":" + frames[0].getLineNumber();
        return failure.getClass().getSimpleName() + " (causa " + root.getClass().getName() + " em " + origin + ")";
    }
}
