package br.com.fintech.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fintech.TestcontainersConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cadastro, confirmação e reenvio pelo servidor real, sem credencial, com o e-mail capturado
 * por um SMTP embutido. Cobre as respostas idênticas para conta nova e existente.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"spring.mail.host=localhost", "spring.mail.port=3025",
                // O SMTP de teste loga os endereços que recebe; o teste de log vale para a aplicação.
                "logging.level.com.icegreen=warn"})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class RegistrationIntegrationTests {

    private static final String PASSWORD = "uma frase longa e secreta de cadastro";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final long MAIL_WAIT_MS = 10_000;

    @RegisterExtension
    static final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void deleteAllUsers() {
        jdbc.sql("DELETE FROM users").update();
    }

    @Test
    void registrationThenTheEmailedLinkVerifiesTheAccountOnce() throws Exception {
        HttpResponse<String> registered = register("Maria Silva", "maria@example.com", PASSWORD);

        assertThat(registered.statusCode()).isEqualTo(202);
        assertThat(registered.body()).isEmpty();
        MimeMessage mail = awaitSingleMail();
        assertThat(mail.getSubject()).isEqualTo("Confirme seu e-mail na Nexa Finance");
        String body = body(mail);
        // Sem o nome digitado: o e-mail vai para um endereço que qualquer um pode informar.
        assertThat(body).startsWith("Olá!\n").doesNotContain("Maria").contains("24 horas")
                .contains("http://localhost:8080/api/v1/auth/email-verification?token=");
        String token = token(body);
        assertThat(userColumn("maria@example.com", "email_verified_at")).isNull();

        HttpResponse<String> verified = get("/api/v1/auth/email-verification?token=" + token);

        assertThat(verified.statusCode()).isEqualTo(200);
        assertThat(json.readTree(verified.body()).get("message").asString()).startsWith("E-mail confirmado");
        assertThat(userColumn("maria@example.com", "email_verified_at")).isNotNull();
        assertThat(eventTypes("maria@example.com")).containsExactly("REGISTERED", "EMAIL_VERIFIED");
        assertThat(jdbc.sql("SELECT DISTINCT origin FROM security_events").query(String.class).list())
                .containsExactly("127.0.0.0");

        HttpResponse<String> reused = get("/api/v1/auth/email-verification?token=" + token);

        assertInvalidToken(reused);
        assertThat(eventTypes("maria@example.com")).containsExactly("REGISTERED", "EMAIL_VERIFIED");
    }

    @Test
    void storedTokenIsOnlyTheHashOfTheEmailedOne() throws Exception {
        register("Maria Silva", "maria@example.com", PASSWORD);
        String token = token(body(awaitSingleMail()));

        String stored = jdbc.sql("SELECT token_hash FROM email_tokens").query(String.class).single();
        String passwordHash = userColumn("maria@example.com", "password_hash");

        assertThat(stored).matches("[0-9a-f]{64}").isNotEqualTo(token);
        assertThat(passwordHash).startsWith("$argon2id$").doesNotContain(PASSWORD);
    }

    @Test
    void expiredUnknownMissingOrOversizedTokensAreAllTheSameInvalidToken() throws Exception {
        register("Maria Silva", "maria@example.com", PASSWORD);
        String token = token(body(awaitSingleMail()));
        jdbc.sql("UPDATE email_tokens SET created_at = now() - interval '2 days', expires_at = now() - interval '1 second'")
                .update();

        assertInvalidToken(get("/api/v1/auth/email-verification?token=" + token));
        assertInvalidToken(get("/api/v1/auth/email-verification?token=" + "x".repeat(43)));
        assertInvalidToken(get("/api/v1/auth/email-verification"));
        assertInvalidToken(get("/api/v1/auth/email-verification?token="));
        assertInvalidToken(get("/api/v1/auth/email-verification?token=" + "x".repeat(5_000)));
        assertThat(userColumn("maria@example.com", "email_verified_at")).isNull();
    }

    @Test
    void registeringAnExistingEmailAnswersTheSameAndOnlyWarnsTheOwner() throws Exception {
        HttpResponse<String> first = register("Maria Silva", "maria@example.com", PASSWORD);
        awaitSingleMail();
        greenMail.reset();

        HttpResponse<String> again = register("Outra Pessoa", "  MARIA@Example.com ", "outra frase longa e secreta");

        assertThat(again.statusCode()).isEqualTo(first.statusCode()).isEqualTo(202);
        assertThat(again.body()).isEqualTo(first.body()).isEmpty();
        assertThat(again.headers().map().keySet()).isEqualTo(first.headers().map().keySet());
        MimeMessage warning = awaitSingleMail();
        assertThat(warning.getAllRecipients()[0].toString()).isEqualTo("maria@example.com");
        assertThat(warning.getSubject()).isEqualTo("Tentativa de cadastro com o seu e-mail");
        assertThat(body(warning)).contains("Olá, Maria Silva!").doesNotContain("token=");
        assertThat(jdbc.sql("SELECT count(*) FROM users").query(Long.class).single()).isEqualTo(1);
        assertThat(userColumn("maria@example.com", "full_name")).isEqualTo("Maria Silva");
    }

    @Test
    void weakPasswordIsRefusedWithTheReasonTheSameWayForNewAndExistingEmail() throws Exception {
        register("Maria Silva", "maria@example.com", PASSWORD);
        awaitSingleMail();

        HttpResponse<String> existing = register("Maria", "maria@example.com", "curta");
        HttpResponse<String> unknown = register("Joana", "joana@example.com", "curta");

        for (HttpResponse<String> response : List.of(existing, unknown)) {
            assertThat(response.statusCode()).isEqualTo(400);
            JsonNode problem = json.readTree(response.body());
            assertThat(problem.get("type").asString()).isEqualTo("urn:nexa-finance:problem:validation-error");
            assertThat(problem.get("errors")).hasSize(1);
            assertThat(problem.get("errors").get(0).get("field").asString()).isEqualTo("password");
            assertThat(problem.get("errors").get(0).get("message").asString())
                    .isEqualTo("A senha precisa ter pelo menos 15 caracteres. Uma frase com algumas palavras "
                            + "é fácil de lembrar e difícil de adivinhar.");
        }
        assertThat(json.readTree(existing.body()).get("errors"))
                .isEqualTo(json.readTree(unknown.body()).get("errors"));
        assertThat(register("Joana", "joana@example.com", "minha joana@example.com segura").body())
                .contains("A senha não pode conter o seu e-mail.");
        assertThat(jdbc.sql("SELECT count(*) FROM users").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void controlCharactersAreRefusedBeforeTheNewOrExistingBranchSoBothAnswerTheSame() throws Exception {
        register("Maria Silva", "maria@example.com", PASSWORD);
        awaitSingleMail();
        greenMail.reset();

        // Escapados no próprio JSON, como um cliente os mandaria: um surrogate solto não sobrevive
        // à codificação UTF-8 do corpo e chegaria ao servidor como "?".
        List<String> names = List.of(
                "Maria\\u0000Silva",
                "Maria\\nVisite http://golpe.example",
                "Maria\\u2028Silva",
                // Surrogate solto: o banco gravaria "?" sem avisar.
                "Maria\\ud800Silva");
        for (String name : names) {
            HttpResponse<String> existing = registerRawName(name, "maria@example.com");
            HttpResponse<String> unknown = registerRawName(name, "joana@example.com");

            assertThat(existing.statusCode()).as(name).isEqualTo(unknown.statusCode()).isEqualTo(400);
            assertThat(json.readTree(existing.body()).get("errors"))
                    .isEqualTo(json.readTree(unknown.body()).get("errors"));
            assertThat(json.readTree(existing.body()).get("errors").findValuesAsString("field"))
                    .containsExactly("fullName");
        }
        HttpResponse<String> nulEmail = register("Joana", "joana\u0000@example.com", PASSWORD);
        assertThat(nulEmail.statusCode()).isEqualTo(400);
        assertThat(greenMail.waitForIncomingEmail(500, 1)).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM users").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void invalidFieldsAreReportedTogether() throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/register",
                "{\"fullName\":\"   \",\"email\":\"nao-e-email\",\"password\":null}");

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode errors = json.readTree(response.body()).get("errors");
        assertThat(errors.findValuesAsString("field")).containsExactly("email", "fullName", "password");
        assertThat(greenMail.waitForIncomingEmail(500, 1)).isFalse();
    }

    @Test
    void resendIssuesANewLinkAndTheOldOneStopsWorking() throws Exception {
        register("Maria Silva", "maria@example.com", PASSWORD);
        String oldToken = token(body(awaitSingleMail()));
        greenMail.reset();

        HttpResponse<String> resent = post("/api/v1/auth/email-verification/resend", "{\"email\":\" Maria@example.com \"}");

        assertThat(resent.statusCode()).isEqualTo(202);
        assertThat(resent.body()).isEmpty();
        String newToken = token(body(awaitSingleMail()));
        assertThat(newToken).isNotEqualTo(oldToken);
        assertInvalidToken(get("/api/v1/auth/email-verification?token=" + oldToken));
        assertThat(get("/api/v1/auth/email-verification?token=" + newToken).statusCode()).isEqualTo(200);
    }

    @Test
    void resendAnswersTheSameAndSendsNothingForUnknownOrAlreadyVerifiedAccounts() throws Exception {
        register("Maria Silva", "maria@example.com", PASSWORD);
        get("/api/v1/auth/email-verification?token=" + token(body(awaitSingleMail())));
        greenMail.reset();

        HttpResponse<String> verified = post("/api/v1/auth/email-verification/resend", "{\"email\":\"maria@example.com\"}");
        HttpResponse<String> unknown = post("/api/v1/auth/email-verification/resend", "{\"email\":\"ninguem@example.com\"}");

        assertThat(verified.statusCode()).isEqualTo(unknown.statusCode()).isEqualTo(202);
        assertThat(verified.body()).isEqualTo(unknown.body()).isEmpty();
        assertThat(greenMail.waitForIncomingEmail(1_000, 1)).isFalse();
    }

    @Test
    void passwordAndTokensNeverReachTheLog(CapturedOutput output) throws Exception {
        String password = "senha-que-nunca-aparece-" + UUID.randomUUID();
        register("Maria Silva", "maria@example.com", password);
        String token = token(body(awaitSingleMail()));
        get("/api/v1/auth/email-verification?token=" + token);
        get("/api/v1/auth/email-verification?token=" + token);
        register("Maria", "maria@example.com", "curta");
        // Caminho da conta existente: aviso ao dono, sem conta nova.
        greenMail.reset();
        register("Outra Pessoa", "maria@example.com", password + "-2");
        awaitSingleMail();
        // O SMTP recebe a mensagem um instante antes de o envio ser logado.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(output.getAll()).contains("E-mail enviado: registration-attempt"));

        assertThat(output.getAll())
                .contains("Evento de segurança REGISTERED userId=")
                .doesNotContain(password)
                .doesNotContain(password + "-2")
                .doesNotContain(token)
                .doesNotContain("maria@example.com");
    }

    private void assertInvalidToken(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.get("type").asString()).isEqualTo("urn:nexa-finance:problem:invalid-token");
        assertThat(problem.get("title").asString()).isEqualTo("Link inválido ou expirado");
        assertThat(problem.get("instance").asString()).isEqualTo("/api/v1/auth/email-verification");
        assertThat(response.body()).doesNotContain("token=");
    }

    private HttpResponse<String> register(String fullName, String email, String password) throws Exception {
        String body = json.writeValueAsString(new RegisterRequest(fullName, email, password));
        return post("/api/v1/auth/register", body);
    }

    /** {@code escapedName} vai como está dentro das aspas do JSON (escapes {@code \\uXXXX} inclusive). */
    private HttpResponse<String> registerRawName(String escapedName, String email) throws Exception {
        return post("/api/v1/auth/register", "{\"fullName\":\"" + escapedName + "\",\"email\":\"" + email
                + "\",\"password\":\"" + PASSWORD + "\"}");
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(jsonBody))
                .build();
        return http.send(request, BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private MimeMessage awaitSingleMail() {
        assertThat(greenMail.waitForIncomingEmail(MAIL_WAIT_MS, 1)).as("e-mail recebido").isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        return greenMail.getReceivedMessages()[0];
    }

    private static String body(MimeMessage mail) throws Exception {
        return ((String) mail.getContent()).replace("\r\n", "\n");
    }

    private static String token(String mailBody) {
        Matcher matcher = TOKEN.matcher(mailBody);
        assertThat(matcher.find()).as("link com token no e-mail").isTrue();
        return matcher.group(1);
    }

    private String userColumn(String email, String column) {
        return jdbc.sql("SELECT " + column + "::text FROM users WHERE email = :email")
                .param("email", email)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private List<String> eventTypes(String email) {
        return jdbc.sql("SELECT e.type FROM security_events e JOIN users u ON u.id = e.user_id"
                        + " WHERE u.email = :email ORDER BY e.occurred_at, e.id")
                .param("email", email)
                .query(String.class)
                .list();
    }
}
