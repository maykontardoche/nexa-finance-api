package br.com.fintech.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fintech.TestcontainersConfiguration;
import jakarta.servlet.Filter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Negação por padrão contra o servidor real e sem credencial (ADR-0005): 401 em rota privada e inexistente, ausência de credenciais no log, lista
 * pública exata, o
 * encaminhamento de ERROR liberado só para renderizar o erro e o {@code traceparent} no 401.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, SecurityIntegrationTests.FailingFilterConfig.class})
@ExtendWith(OutputCaptureExtension.class)
class SecurityIntegrationTests {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
    private static final String BEARER_TOKEN = "segredo-de-teste-c41d"; // gitleaks:allow

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    @Autowired
    ApplicationContext context;

    @Test
    void publicListIsExactlyTheOneInAdr0005() {
        // Abrir rota pública exige mudar esta lista de propósito.
        assertThat(SecurityConfig.PUBLIC_PATHS).containsExactly(
                "/actuator/health", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**");
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "GET, /actuator/health, 200", "HEAD, /actuator/health, 200", "GET, /v3/api-docs, 200",
            "GET, /v3/api-docs/swagger-config, 200", "GET, /swagger-ui.html, 302",
            "GET, /swagger-ui/index.html, 200"})
    void publicRoutesAnswerWithoutCredential(String method, String path, int status) throws Exception {
        assertThat(send(method, path, Map.of()).statusCode()).isEqualTo(status);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET, /api/v1/qualquer",
            "GET, /nao-existe",
            "DELETE, /api/v1/contas/1",
            "POST, /actuator/health",      // público só para GET
            "GET, /actuator/env",          // nenhum outro endpoint do Actuator é público
            "GET, /actuator/beans",
            "GET, /actuator",              // nem o índice do Actuator
            "GET, /error",                 // só o dispatch de ERROR é liberado, não o caminho
            "GET, /v3/api-docs.yaml"})
    void everythingElseIsUnauthorizedWithoutRevealingTheRoute(String method, String path) throws Exception {
        HttpResponse<String> response = send(method, path, Map.of());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("application/problem+json"));
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.get("type").asString()).isEqualTo("urn:nexa-finance:problem:unauthorized");
        assertThat(problem.get("title").asString()).isEqualTo("Não autenticado");
        assertThat(problem.get("status").asInt()).isEqualTo(401);
        assertThat(problem.get("instance").asString()).isEqualTo(path);
        assertThat(problem.get("traceId").asString()).matches("[0-9a-f]{32}");
        assertThat(response.body()).doesNotContain("Exception", "org.", "java.");
        assertSecurityHeaders(response);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/v1//x", "/api/v1/x;jsessionid=1", "/api/v1/a%2Fb", "/api/v1/a%5Cb",
            "/api/v1/%2e%2e/x"})
    void requestsRejectedBeforeSecurityStillFollowTheContract(String path) throws Exception {
        // Recusadas pelo firewall do Spring Security (ou, no %2F, repassadas pelo Tomcat até ele):
        // o corpo vem do error controller, e os headers de segurança não podem faltar.
        HttpResponse<String> response = send("GET", path, Map.of());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("application/problem+json"));
        assertThat(json.readTree(response.body()).get("type").asString())
                .isEqualTo("urn:nexa-finance:problem:bad-request");
        assertSecurityHeaders(response);
    }

    @Test
    void invalidCredentialIsStillUnauthorized() throws Exception {
        // Não existe autenticador até a Fase 2: nenhum header vira identidade.
        HttpResponse<String> response = send("GET", "/api/v1/qualquer",
                Map.of("Authorization", "Bearer " + BEARER_TOKEN));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void incomingTraceparentIsHonoredOnUnauthorized() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/qualquer", Map.of("traceparent", TRACEPARENT));

        assertThat(json.readTree(response.body()).get("traceId").asString()).isEqualTo(TRACE_ID);
    }

    @Test
    void errorOutsideMvcIsRenderedThroughTheErrorDispatch(CapturedOutput output) throws Exception {
        HttpResponse<String> response = send("GET", FailingFilterConfig.PATH, Map.of("traceparent", TRACEPARENT));

        // Sem credencial e mesmo assim 500 (não 401): o dispatch de ERROR é liberado para renderizar.
        assertThat(response.statusCode()).isEqualTo(500);
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.get("type").asString()).isEqualTo("urn:nexa-finance:problem:internal-error");
        assertThat(problem.get("instance").asString()).isEqualTo(FailingFilterConfig.PATH);
        assertThat(problem.get("traceId").asString()).isEqualTo(TRACE_ID);
        assertThat(response.body()).doesNotContain(FailingFilterConfig.LEAKY_MESSAGE, "Exception", "java.");
        assertSecurityHeaders(response);
        String marker = "Erro fora do MVC em " + FailingFilterConfig.PATH + " (traceId=" + TRACE_ID + ")";
        // A correlação do MDC na própria linha é a da requisição, não um trace órfão.
        assertThat(logLine(output, marker)).contains("[" + TRACE_ID + "-");
        assertThat(output.getOut()).containsSubsequence(marker,
                "java.lang.IllegalStateException: " + FailingFilterConfig.LEAKY_MESSAGE);
    }

    @Test
    void credentialsNeverReachTheLog(CapturedOutput output) throws Exception {
        // Um 401, um 500 com stack no log e uma rota pública, todos com Authorization.
        Map<String, String> headers = Map.of("Authorization", "Bearer " + BEARER_TOKEN);
        send("GET", "/api/v1/qualquer", headers);
        send("GET", FailingFilterConfig.PATH, headers);
        send("GET", "/actuator/health", headers);

        assertThat(output.getAll())
                .contains("Erro fora do MVC em " + FailingFilterConfig.PATH)
                .doesNotContain(BEARER_TOKEN)
                .doesNotContain(TestcontainersConfiguration.DB_PASSWORD);
    }

    @Test
    void thereIsExactlyOneFilterChainAndNoBypass() {
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(WebSecurityCustomizer.class)).isEmpty();
    }

    @Test
    void noInMemoryUserIsConfigured() {
        // Sem o UserDetailsServiceAutoConfiguration o Boot não gera usuário nem loga senha gerada.
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }

    private static void assertSecurityHeaders(HttpResponse<String> response) {
        assertThat(response.headers().allValues("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
    }

    private static String logLine(CapturedOutput output, String marker) {
        return output.getOut().lines()
                .filter(line -> line.contains(marker))
                .reduce((first, last) -> last)
                .orElseThrow(() -> new AssertionError("linha de log não encontrada: " + marker));
    }

    private HttpResponse<String> send(String method, String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, BodyPublishers.noBody());
        headers.forEach(request::header);
        return http.send(request.build(), BodyHandlers.ofString());
    }

    /**
     * Filtro que falha antes da segurança (depois só do filtro de observação): o erro chega ao
     * cliente apenas pelo encaminhamento de ERROR para o error controller.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingFilterConfig {

        static final String PATH = "/filter-boom";
        static final String LEAKY_MESSAGE =
                "SQL falhou: select * from segredo at br.com.fintech.Dao (java.sql.SQLException)";

        @Bean
        FilterRegistrationBean<Filter> failingFilter() {
            Filter filter = (request, response, chain) -> {
                throw new IllegalStateException(LEAKY_MESSAGE);
            };
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
            registration.addUrlPatterns(PATH);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            return registration;
        }
    }
}
