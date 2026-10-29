package br.com.fintech.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fintech.TestcontainersConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Endpoints de plataforma contra o servidor real e sem credencial: health e documentação
 * OpenAPI. O último teste para o banco para provar o DOWN; por isso a
 * ordem é fixa e o contexto (com o container parado) é descartado ao fim da classe.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestMethodOrder(OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformEndpointsIntegrationTests {

    /** Guarda contra o timeout padrão do Hikari (30 s): com 2 s configurados, o DOWN sai em ~2 s. */
    private static final Duration DOWN_BUDGET = Duration.ofSeconds(5);
    /** Abaixo disso o DOWN não passou pelo connection-timeout, e o teste não provaria o que diz. */
    private static final Duration TIMEOUT_PATH_FLOOR = Duration.ofMillis(1500);

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    @Autowired
    PostgreSQLContainer postgres;

    @Autowired
    DataSource dataSource;

    @Test
    @Order(1)
    void healthIsUpWithOnlyTheAggregateStatus() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body())).isEqualTo(json.readTree("{\"status\":\"UP\"}"));
    }

    @Test
    @Order(2)
    void apiDocsAreOpenApi31WithTheProblemSchemaAndOnlyTheApi() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode docs = json.readTree(response.body());
        assertThat(docs.get("openapi").asString()).startsWith("3.1");
        assertThat(docs.at("/info/title").asString()).isEqualTo("Nexa Finance API");
        JsonNode problem = docs.at("/components/schemas/ProblemDetail");
        assertThat(problem.isMissingNode()).isFalse();
        assertThat(problem.get("properties").propertyNames())
                .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance", "traceId", "errors");
        // Só /api/**: nem o /error do container nem o Actuator entram no contrato documentado.
        docs.path("paths").propertyNames().forEach(path -> assertThat(path).startsWith("/api/"));
    }

    @Test
    @Order(3)
    void documentedProblemSchemaMatchesARealErrorBody() throws Exception {
        // Liga o schema escrito à mão ao corpo que a API devolve de fato (aqui, o 401).
        JsonNode schema = json.readTree(get("/v3/api-docs").body()).at("/components/schemas/ProblemDetail");
        JsonNode body = json.readTree(get("/api/v1/qualquer").body());

        assertThat(body.propertyNames()).isSubsetOf(schema.get("properties").propertyNames());
        schema.get("required").forEach(field -> assertThat(body.has(field.asString()))
                .as("campo obrigatório %s no corpo real", field.asString()).isTrue());
    }

    @Test
    @Order(3)
    void swaggerUiOpensWithoutCredential() throws Exception {
        HttpResponse<String> redirect = get("/swagger-ui.html");
        HttpResponse<String> page = get("/swagger-ui/index.html");

        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.headers().firstValue("Location")).hasValueSatisfying(
                location -> assertThat(location).endsWith("/swagger-ui/index.html"));
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("Swagger UI");
    }

    @Test
    @Order(4)
    void healthIsDownFastWithOnlyTheStatusWhenTheDatabaseStops() throws Exception {
        postgres.stop();
        // Sem isto, a conexão do pool usada há menos de 500 ms não é revalidada pelo Hikari e o
        // health falha na hora, sem passar pelo connection-timeout. Esvaziando o pool, o health
        // precisa abrir uma conexão nova, que é o caminho de um banco que cai de verdade.
        dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().softEvictConnections();

        long start = System.nanoTime();
        HttpResponse<String> response = get("/actuator/health");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(json.readTree(response.body())).isEqualTo(json.readTree("{\"status\":\"DOWN\"}"));
        assertThat(response.body()).doesNotContain("Exception", "jdbc", "postgres", "Connection");
        assertThat(elapsed).isBetween(TIMEOUT_PATH_FLOOR, DOWN_BUDGET);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(request, BodyHandlers.ofString());
    }
}
