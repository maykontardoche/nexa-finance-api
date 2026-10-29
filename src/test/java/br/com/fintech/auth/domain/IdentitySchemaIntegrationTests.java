package br.com.fintech.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import br.com.fintech.TestcontainersConfiguration;
import br.com.fintech.user.domain.User;
import br.com.fintech.user.domain.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Schema de identidade contra o Postgres real: a migration aplica, as entidades batem com ela
 * ({@code ddl-auto=validate}) e as regras que só existem no banco (UNIQUE, CHECK e cascata)
 * recusam o que devem recusar.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class IdentitySchemaIntegrationTests {

    // O Postgres guarda microssegundos; o Instant do JDK tem nanossegundos.
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Instant IN_A_WEEK = NOW.plus(Duration.ofDays(7));

    @Autowired
    Flyway flyway;

    @Autowired
    Environment environment;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    UserRepository users;

    @Autowired
    SessionRepository sessions;

    @Autowired
    EmailTokenRepository emailTokens;

    @Autowired
    SecurityEventRepository securityEvents;

    @AfterEach
    void deleteAllIdentityRows() {
        // Filhos primeiro, sem depender da cascata: se ela quebrar, só o teste dela falha.
        for (String table : new String[] {"security_events", "email_tokens", "sessions", "users"}) {
            jdbc.sql("DELETE FROM " + table).update();
        }
    }

    @Test
    void identityMigrationIsAppliedAndHibernateOnlyValidatesTheSchema() {
        assertThat(flyway.info().applied())
                .extracting(MigrationInfo::getVersion)
                .extracting(Object::toString)
                .contains("1");
        assertThat(flyway.info().pending()).isEmpty();
        // O contexto só sobe se as entidades baterem com o schema da migration.
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }

    @Test
    void everyEntityRoundTripsThroughTheDatabase() {
        User user = newUser("maria@example.com");
        UUID userId = user.getId();
        Session session = sessions.save(new Session(userId, hash(), NOW, IN_A_WEEK, "Firefox 140 / Windows", "203.0.113.0"));
        EmailToken token = emailTokens.save(new EmailToken(
                userId, EmailTokenPurpose.EMAIL_CHANGE, hash(), "maria.nova@example.com", NOW, NOW.plus(Duration.ofHours(24))));
        SecurityEvent event = securityEvents.save(new SecurityEvent(
                userId, SecurityEventType.LOGIN_SUCCEEDED, NOW, "Firefox 140 / Windows", "203.0.113.0", session.getId()));

        User storedUser = users.findByEmail("maria@example.com").orElseThrow();
        assertThat(storedUser.getId()).isEqualTo(userId);
        assertThat(storedUser.getFullName()).isEqualTo("Maria Silva");
        assertThat(storedUser.getFailedLogins()).isZero();
        assertThat(storedUser.getCreatedAt()).isEqualTo(NOW);
        assertThat(storedUser.getEmailVerifiedAt()).isNull();

        Session storedSession = sessions.findById(session.getId()).orElseThrow();
        assertThat(storedSession.getUserId()).isEqualTo(userId);
        assertThat(storedSession.getRefreshHash()).isEqualTo(session.getRefreshHash());
        assertThat(storedSession.getLastUsedAt()).isEqualTo(NOW);
        assertThat(storedSession.getExpiresAt()).isEqualTo(IN_A_WEEK);
        assertThat(storedSession.getRevokedAt()).isNull();
        assertThat(storedSession.getRevokeReason()).isNull();

        EmailToken storedToken = emailTokens.findById(token.getId()).orElseThrow();
        assertThat(storedToken.getPurpose()).isEqualTo(EmailTokenPurpose.EMAIL_CHANGE);
        assertThat(storedToken.getNewEmail()).isEqualTo("maria.nova@example.com");
        assertThat(storedToken.getUsedAt()).isNull();

        SecurityEvent storedEvent = securityEvents.findById(event.getId()).orElseThrow();
        assertThat(storedEvent.getType()).isEqualTo(SecurityEventType.LOGIN_SUCCEEDED);
        assertThat(storedEvent.getSessionId()).isEqualTo(session.getId());
        assertThat(storedEvent.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    void generatedIdsAreUuidVersion7() {
        assertThat(newUser("maria@example.com").getId().version()).isEqualTo(7);
    }

    @Test
    void duplicateEmailViolatesTheUniqueConstraint() {
        newUser("maria@example.com");

        assertThatThrownBy(() -> newUser("maria@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("users_email_uk");
    }

    @Test
    void duplicateCredentialHashesViolateTheUniqueConstraints() {
        UUID userId = newUser("maria@example.com").getId();
        String refreshHash = hash();
        String tokenHash = hash();
        sessions.save(new Session(userId, refreshHash, NOW, IN_A_WEEK, null, null));
        emailTokens.save(new EmailToken(userId, EmailTokenPurpose.PASSWORD_RESET, tokenHash, null, NOW, IN_A_WEEK));

        assertThatThrownBy(() -> sessions.saveAndFlush(new Session(userId, refreshHash, NOW, IN_A_WEEK, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("sessions_refresh_hash_uk");
        assertThatThrownBy(() -> emailTokens.saveAndFlush(
                        new EmailToken(userId, EmailTokenPurpose.PASSWORD_RESET, tokenHash, null, NOW, IN_A_WEEK)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("email_tokens_token_hash_uk");
    }

    @Test
    void constraintViolationsNeverCarryTheDuplicatedValue(CapturedOutput output) {
        String email = "maria.sigilo@example.com";
        String refreshHash = hash();
        UUID userId = newUser(email).getId();
        sessions.save(new Session(userId, refreshHash, NOW, IN_A_WEEK, null, null));

        Throwable duplicatedEmail = catchThrowable(() -> newUser(email));
        Throwable duplicatedHash = catchThrowable(
                () -> sessions.saveAndFlush(new Session(userId, refreshHash, NOW, IN_A_WEEK, null, null)));
        // Numa violação de CHECK, o Detail do Postgres traria a linha inteira, com o hash da senha.
        String passwordHash = "$argon2id$hash-sigiloso-" + UUID.randomUUID();
        Throwable failedCheck = catchThrowable(() -> jdbc.sql(
                        "INSERT INTO users (id, full_name, email, password_hash, failed_logins, created_at, updated_at, version)"
                                + " VALUES (gen_random_uuid(), 'X', 'x@example.com', :hash, -1, now(), now(), 0)")
                .param("hash", passwordHash)
                .update());

        // O nome da constraint basta para tratar o erro; o valor não sai do banco.
        assertThat(duplicatedEmail).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("users_email_uk");
        assertThat(duplicatedHash).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("sessions_refresh_hash_uk");
        assertThat(failedCheck).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("users_failed_logins_ck");
        for (Throwable failure : new Throwable[] {duplicatedEmail, duplicatedHash, failedCheck}) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                assertThat(cause.getMessage()).doesNotContain(email).doesNotContain(refreshHash).doesNotContain(passwordHash);
            }
        }
        assertThat(output.getAll()).contains("users_email_uk")
                .doesNotContain(email).doesNotContain(refreshHash).doesNotContain(passwordHash);
    }

    @Test
    void deletingAUserDeletesItsSessionsTokensAndEventsButNoOneElses() {
        UUID maria = newUser("maria@example.com").getId();
        UUID joao = newUser("joao@example.com").getId();
        for (UUID owner : new UUID[] {maria, joao}) {
            sessions.save(new Session(owner, hash(), NOW, IN_A_WEEK, null, null));
            emailTokens.save(new EmailToken(owner, EmailTokenPurpose.EMAIL_VERIFICATION, hash(), null, NOW, IN_A_WEEK));
            securityEvents.save(new SecurityEvent(owner, SecurityEventType.REGISTERED, NOW, null, null, null));
        }
        // A sessão revogada também precisa sair (o índice de user_id não é parcial por isso).
        jdbc.sql("UPDATE sessions SET revoked_at = now(), revoke_reason = 'LOGOUT' WHERE user_id = :userId")
                .param("userId", maria)
                .update();

        users.deleteById(maria);

        assertThat(users.existsById(maria)).isFalse();
        for (String table : new String[] {"sessions", "email_tokens", "security_events"}) {
            assertThat(countByUser(table, maria)).as(table + " de quem foi excluído").isZero();
            assertThat(countByUser(table, joao)).as(table + " de outro usuário").isEqualTo(1);
        }
    }

    static Stream<Arguments> rowsTheChecksMustRefuse() {
        String user = "INSERT INTO users (id, full_name, email, password_hash, failed_logins, created_at, updated_at, version) ";
        String session = "INSERT INTO sessions (id, user_id, refresh_hash, created_at, last_used_at, expires_at, revoked_at, revoke_reason) ";
        String token = "INSERT INTO email_tokens (id, user_id, purpose, token_hash, new_email, expires_at, created_at) ";
        String event = "INSERT INTO security_events (id, user_id, type, occurred_at) ";
        String hash = "'" + "ab".repeat(32) + "'";
        return Stream.of(
                Arguments.of("nome só com espaços",
                        user + "VALUES (gen_random_uuid(), '   ', 'x@example.com', 'h', 0, now(), now(), 0)", "users_full_name_ck"),
                Arguments.of("e-mail com espaço nas pontas",
                        user + "VALUES (gen_random_uuid(), 'X', ' x@example.com', 'h', 0, now(), now(), 0)", "users_email_ck"),
                Arguments.of("e-mail com maiúscula",
                        user + "VALUES (gen_random_uuid(), 'X', 'Maria@example.com', 'h', 0, now(), now(), 0)", "users_email_ck"),
                Arguments.of("e-mail vazio",
                        user + "VALUES (gen_random_uuid(), 'X', '', 'h', 0, now(), now(), 0)", "users_email_ck"),
                Arguments.of("contador de falhas negativo",
                        user + "VALUES (gen_random_uuid(), 'X', 'x@example.com', 'h', -1, now(), now(), 0)", "users_failed_logins_ck"),
                Arguments.of("motivo de revogação fora do enum",
                        session + "VALUES (gen_random_uuid(), :userId, " + hash + ", now(), now(), now() + interval '7 days', now(), 'EXPIRED')",
                        "sessions_revoke_reason_ck"),
                Arguments.of("sessão revogada sem motivo",
                        session + "VALUES (gen_random_uuid(), :userId, " + hash + ", now(), now(), now() + interval '7 days', now(), NULL)",
                        "sessions_revocation_ck"),
                Arguments.of("motivo em sessão ativa",
                        session + "VALUES (gen_random_uuid(), :userId, " + hash + ", now(), now(), now() + interval '7 days', NULL, 'LOGOUT')",
                        "sessions_revocation_ck"),
                Arguments.of("hash de renovação que não é SHA-256 hex",
                        session + "VALUES (gen_random_uuid(), :userId, '" + "AB".repeat(32) + "', now(), now(), now() + interval '7 days', NULL, NULL)",
                        "sessions_refresh_hash_ck"),
                Arguments.of("sessão que expira antes de começar",
                        session + "VALUES (gen_random_uuid(), :userId, " + hash + ", now(), now(), now(), NULL, NULL)",
                        "sessions_expiry_ck"),
                Arguments.of("finalidade de token fora do enum",
                        token + "VALUES (gen_random_uuid(), :userId, 'MAGIC_LINK', " + hash + ", NULL, now() + interval '1 day', now())",
                        "email_tokens_purpose_ck"),
                Arguments.of("hash de token curto",
                        token + "VALUES (gen_random_uuid(), :userId, 'PASSWORD_RESET', 'abc', NULL, now() + interval '1 day', now())",
                        "email_tokens_token_hash_ck"),
                Arguments.of("troca de e-mail sem o novo endereço",
                        token + "VALUES (gen_random_uuid(), :userId, 'EMAIL_CHANGE', " + hash + ", NULL, now() + interval '1 day', now())",
                        "email_tokens_new_email_ck"),
                Arguments.of("novo endereço em token que não é de troca",
                        token + "VALUES (gen_random_uuid(), :userId, 'EMAIL_VERIFICATION', " + hash + ", 'y@example.com', now() + interval '1 day', now())",
                        "email_tokens_new_email_ck"),
                Arguments.of("novo endereço com maiúscula",
                        token + "VALUES (gen_random_uuid(), :userId, 'EMAIL_CHANGE', " + hash + ", 'Y@example.com', now() + interval '1 day', now())",
                        "email_tokens_new_email_format_ck"),
                Arguments.of("token que expira antes de ser criado",
                        token + "VALUES (gen_random_uuid(), :userId, 'PASSWORD_RESET', " + hash + ", NULL, now() - interval '1 second', now())",
                        "email_tokens_expiry_ck"),
                Arguments.of("tipo de evento fora do enum",
                        event + "VALUES (gen_random_uuid(), :userId, 'HACKED', now())", "security_events_type_ck"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rowsTheChecksMustRefuse")
    void checkConstraintsRefuseInvalidRows(String description, String insert, String constraint) {
        UUID userId = newUser("maria@example.com").getId();

        assertThatThrownBy(() -> jdbc.sql(insert).param("userId", userId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining(constraint);
    }

    @Test
    void everyEnumValueOfTheEntitiesIsAcceptedByTheChecks() {
        UUID userId = newUser("maria@example.com").getId();

        for (RevokeReason reason : RevokeReason.values()) {
            jdbc.sql("INSERT INTO sessions (id, user_id, refresh_hash, created_at, last_used_at, expires_at, revoked_at, revoke_reason)"
                            + " VALUES (gen_random_uuid(), :userId, :hash, now(), now(), now() + interval '7 days', now(), :reason)")
                    .param("userId", userId).param("hash", hash()).param("reason", reason.name())
                    .update();
        }
        for (EmailTokenPurpose purpose : EmailTokenPurpose.values()) {
            String newEmail = purpose == EmailTokenPurpose.EMAIL_CHANGE ? "maria.nova@example.com" : null;
            emailTokens.save(new EmailToken(userId, purpose, hash(), newEmail, NOW, IN_A_WEEK));
        }
        for (SecurityEventType type : SecurityEventType.values()) {
            securityEvents.save(new SecurityEvent(userId, type, NOW, null, null, null));
        }

        assertThat(countByUser("sessions", userId)).isEqualTo(RevokeReason.values().length);
        assertThat(countByUser("email_tokens", userId)).isEqualTo(EmailTokenPurpose.values().length);
        assertThat(countByUser("security_events", userId)).isEqualTo(SecurityEventType.values().length);
    }

    private User newUser(String email) {
        return users.saveAndFlush(new User("Maria Silva", email, "$argon2id$hash-ficticio", NOW));
    }

    private long countByUser(String table, UUID userId) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id = :userId")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    /** SHA-256 em hex fictício e único (64 caracteres minúsculos). */
    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
