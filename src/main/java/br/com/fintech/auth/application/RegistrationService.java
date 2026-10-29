package br.com.fintech.auth.application;

import br.com.fintech.auth.domain.EmailToken;
import br.com.fintech.auth.domain.EmailTokenPurpose;
import br.com.fintech.auth.domain.EmailTokenRepository;
import br.com.fintech.auth.domain.PasswordPolicy;
import br.com.fintech.auth.domain.SecurityEventType;
import br.com.fintech.auth.infrastructure.CredentialHasher;
import br.com.fintech.shared.mail.MailNotifier;
import br.com.fintech.shared.web.RequestOrigin;
import br.com.fintech.user.domain.EmailAddress;
import br.com.fintech.user.domain.User;
import br.com.fintech.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cadastro, confirmação de e-mail e reenvio da confirmação.
 *
 * <p>Cadastro e reenvio respondem igual exista ou não a conta: quem chama não descobre quais
 * e-mails estão cadastrados. Quem fica sabendo é o dono, por e-mail.
 */
@Service
public class RegistrationService {

    static final String PASSWORD_FIELD = "password";
    private static final String EMAIL_UNIQUE = "users_email_uk";
    // Um token legítimo tem 43 caracteres; nada maior que isso precisa ser hasheado e buscado.
    private static final int MAX_TOKEN_LENGTH = 128;

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository users;
    private final EmailTokenRepository tokens;
    private final PasswordPolicy policy;
    private final CredentialHasher hasher;
    private final MailNotifier mail;
    private final AuthMails mails;
    private final SecurityEventRecorder events;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public RegistrationService(UserRepository users, EmailTokenRepository tokens, PasswordPolicy policy,
            CredentialHasher hasher, MailNotifier mail, AuthMails mails, SecurityEventRecorder events,
            TransactionTemplate transaction, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.policy = policy;
        this.hasher = hasher;
        this.mail = mail;
        this.mails = mails;
        this.events = events;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * Cria a conta e manda o link de confirmação; se o e-mail já tem conta, só avisa o dono.
     *
     * @throws PasswordRejectedException se a senha não passa na política (igual para os dois casos)
     */
    public void register(RegisterCommand command, RequestOrigin origin) {
        String email = EmailAddress.normalize(command.email());
        String fullName = command.fullName().strip();
        policy.check(command.password(), email).ifPresent(violation -> {
            throw new PasswordRejectedException(PASSWORD_FIELD, violation);
        });
        // O hash roda nos dois caminhos: sem ele, e-mail já cadastrado responderia bem mais rápido.
        String passwordHash = hasher.hashPassword(command.password());
        try {
            transaction.executeWithoutResult(status -> createOrWarnOwner(fullName, email, passwordHash, origin));
        } catch (DataIntegrityViolationException e) {
            if (!violates(e, EMAIL_UNIQUE)) {
                throw e;
            }
            // Outro cadastro com o mesmo e-mail gravou entre a busca e o insert: mesma resposta.
            log.info("Cadastro concorrente com e-mail já cadastrado descartado");
        }
    }

    /** @throws InvalidTokenException token ausente, desconhecido, expirado ou já usado */
    @Transactional
    public void verifyEmail(@Nullable String token, RequestOrigin origin) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new InvalidTokenException();
        }
        Instant now = clock.instant();
        EmailToken found = tokens.findByTokenHashAndPurpose(hasher.sha256Hex(token), EmailTokenPurpose.EMAIL_VERIFICATION)
                .orElseThrow(InvalidTokenException::new);
        // Checagem barata antes; o consumo no banco decide de vez (corrida entre dois cliques).
        if (!found.isUsableAt(now) || tokens.consume(found.getId(), now) == 0) {
            throw new InvalidTokenException();
        }
        User user = users.findById(found.getUserId()).orElseThrow(InvalidTokenException::new);
        if (user.verifyEmail(now)) {
            events.record(found.getUserId(), SecurityEventType.EMAIL_VERIFIED, origin);
        }
    }

    /** Novo link para conta ainda não confirmada; sem efeito (e sem aviso) nos outros casos. */
    @Transactional
    public void resendVerification(String email) {
        users.findByEmail(EmailAddress.normalize(email))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    tokens.deleteUnused(user.getId(), EmailTokenPurpose.EMAIL_VERIFICATION);
                    issueVerification(user, clock.instant());
                });
    }

    private void createOrWarnOwner(String fullName, String email, String passwordHash, RequestOrigin origin) {
        Optional<User> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            User owner = existing.get();
            mail.sendAfterCommit(mails.registrationAttempt(owner.getEmail(), owner.getFullName()));
            return;
        }
        Instant now = clock.instant();
        User user = users.saveAndFlush(new User(fullName, email, passwordHash, now));
        issueVerification(user, now);
        events.record(user.getId(), SecurityEventType.REGISTERED, origin);
    }

    private void issueVerification(User user, Instant now) {
        String token = hasher.newSecret();
        tokens.save(EmailToken.issue(user.getId(), EmailTokenPurpose.EMAIL_VERIFICATION, hasher.sha256Hex(token),
                null, now));
        mail.sendAfterCommit(mails.emailVerification(user.getEmail(), token));
    }

    private static boolean violates(Throwable failure, String constraint) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
