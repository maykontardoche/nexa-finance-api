package br.com.fintech.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import br.com.fintech.TestcontainersConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato de erro (formato e ausência de detalhe interno) para requisições autenticadas (ADR-0005: sem credencial, tudo é 401 antes do roteamento).
 * MockMvc com a cadeia completa de filtros, inclusive segurança e observação, e um usuário
 * simulado por {@code @WithMockUser}, porque ainda não existe mecanismo de login (Fase 2). O que
 * só o servidor real prova (401, dispatch de ERROR, lista pública) fica em
 * {@code SecurityIntegrationTests}.
 *
 * <p>O controller de sondagem é aninhado e importado aqui: o {@code TestTypeExcludeFilter} do Boot
 * o tira do component scan, então ele não aparece em outros contextos nem no OpenAPI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ErrorContractTests.ErrorProbeController.class})
@ExtendWith(OutputCaptureExtension.class)
@WithMockUser
class ErrorContractTests {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
    private static final String PAYLOAD = "/api/v1/test-errors/payload";
    /** Nada de exceção, stack, SQL ou nome de classe/pacote no corpo. */
    private static final List<String> FORBIDDEN_IN_BODY =
            List.of("Exception", "at br.", "SQL", "org.", "java.", "segredo");

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    static Stream<Arguments> errorCases() {
        return Stream.of(
                Arguments.of("400 JSON malformado", "POST", PAYLOAD, "application/json", "{nao-e-json",
                        400, "bad-request", "Requisição malformada"),
                Arguments.of("400 validação", "POST", PAYLOAD, "application/json",
                        "{\"name\":\" \",\"email\":\"nao-e-email\"}",
                        400, "validation-error", "Requisição inválida"),
                Arguments.of("400 validação de parâmetro", "GET", "/api/v1/test-errors/params?n=0", null, null,
                        400, "validation-error", "Requisição inválida"),
                Arguments.of("400 conversão de tipo", "GET", "/api/v1/test-errors/model?age=abc", null, null,
                        400, "validation-error", "Requisição inválida"),
                Arguments.of("403 sem permissão (@PreAuthorize)", "GET", "/api/v1/test-errors/admin-only", null, null,
                        403, "forbidden", "Acesso negado"),
                Arguments.of("404 rota inexistente", "GET", "/api/v1/nao-existe", null, null,
                        404, "not-found", "Recurso não encontrado"),
                Arguments.of("404 índice do Actuator (discovery)", "GET", "/actuator", null, null,
                        404, "not-found", "Recurso não encontrado"),
                Arguments.of("404 endpoint do Actuator não exposto (env)", "GET", "/actuator/env", null, null,
                        404, "not-found", "Recurso não encontrado"),
                Arguments.of("404 endpoint do Actuator não exposto (beans)", "GET", "/actuator/beans", null, null,
                        404, "not-found", "Recurso não encontrado"),
                Arguments.of("404 declarado por @ResponseStatus", "GET", "/api/v1/test-errors/missing", null, null,
                        404, "not-found", "Recurso não encontrado"),
                Arguments.of("405 método não suportado", "DELETE", "/api/v1/test-errors/ok", null, null,
                        405, "method-not-allowed", "Método não permitido"),
                Arguments.of("415 content-type inválido", "POST", PAYLOAD, "text/plain", "oi",
                        415, "unsupported-media-type", "Tipo de conteúdo não suportado"),
                Arguments.of("500 no controller", "GET", "/api/v1/test-errors/boom", null, null,
                        500, "internal-error", "Erro interno"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errorCases")
    void everyErrorFollowsTheProblemContract(String scenario, String method, String path,
            String contentType, String body, int status, String slug, String title) throws Exception {
        // Accept-Language em inglês: o texto continua pt-BR (locale fixo).
        MockHttpServletResponse response = perform(method, path, contentType, body,
                Map.of("Accept-Language", "en-US"));

        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        String content = response.getContentAsString(StandardCharsets.UTF_8);
        JsonNode problem = json.readTree(content);
        assertThat(problem.get("type").asString()).isEqualTo("urn:nexa-finance:problem:" + slug);
        assertThat(problem.get("title").asString()).isEqualTo(title);
        assertThat(problem.get("status").asInt()).isEqualTo(status);
        assertThat(problem.get("detail").asString()).isNotBlank();
        assertThat(problem.get("instance").asString()).isEqualTo(URI.create(path).getPath());
        assertThat(problem.get("traceId").asString()).matches("[0-9a-f]{32}");
        assertThat(problem.has("errors")).isEqualTo("validation-error".equals(slug));
        assertThat(content).doesNotContain(FORBIDDEN_IN_BODY);
    }

    @Test
    void validationListsEveryViolationInPortuguese() throws Exception {
        JsonNode errors = errorsOf(perform("POST", PAYLOAD, "application/json",
                "{\"name\":\"\",\"email\":\"nao-e-email\"}", Map.of("Accept-Language", "en-US")));

        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).get("field").asString()).isEqualTo("email");
        assertThat(errors.get(0).get("message").asString()).isEqualTo("deve ser um endereço de e-mail bem formado");
        assertThat(errors.get(1).get("field").asString()).isEqualTo("name");
        assertThat(errors.get(1).get("message").asString()).isEqualTo("não deve estar em branco");
    }

    @Test
    void parameterValidationNamesTheParameter() throws Exception {
        JsonNode errors = errorsOf(perform("GET", "/api/v1/test-errors/params?n=0", null, null, Map.of()));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).get("field").asString()).isEqualTo("n");
        assertThat(errors.get(0).get("message").asString()).isEqualTo("deve ser maior que ou igual à 1");
    }

    @Test
    void conversionFailureHidesTheConverterMessage() throws Exception {
        JsonNode errors = errorsOf(perform("GET", "/api/v1/test-errors/model?age=abc", null, null, Map.of()));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).get("field").asString()).isEqualTo("age");
        assertThat(errors.get(0).get("message").asString()).isEqualTo("valor inválido");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void roleThatSatisfiesPreAuthorizePasses() throws Exception {
        assertThat(perform("GET", "/api/v1/test-errors/admin-only", null, null, Map.of()).getStatus())
                .isEqualTo(200);
    }

    @Test
    void methodNotAllowedKeepsAllowHeader() throws Exception {
        MockHttpServletResponse response = perform("DELETE", "/api/v1/test-errors/ok", null, null, Map.of());

        assertThat(response.getHeader("Allow")).contains("GET");
    }

    @Test
    void controllersAnswerOnlyUnderVersionedPrefix() throws Exception {
        MockHttpServletResponse versioned = perform("GET", "/api/v1/test-errors/ok", null, null, Map.of());
        MockHttpServletResponse unversioned = perform("GET", "/test-errors/ok", null, null, Map.of());

        assertThat(versioned.getStatus()).isEqualTo(200);
        assertThat(versioned.getContentAsString()).isEqualTo("ok");
        assertThat(unversioned.getStatus()).isEqualTo(404);
    }

    @Test
    void incomingTraceparentIsHonoredInTheBody() throws Exception {
        MockHttpServletResponse response = perform("GET", "/api/v1/nao-existe", null, null,
                Map.of("traceparent", TRACEPARENT));

        assertThat(json.readTree(response.getContentAsString()).get("traceId").asString()).isEqualTo(TRACE_ID);
    }

    @Test
    void unexpectedErrorIsLoggedWithStackAndTraceIdOnlyOnTheServer(CapturedOutput output) throws Exception {
        MockHttpServletResponse response = perform("GET", "/api/v1/test-errors/boom", null, null,
                Map.of("traceparent", TRACEPARENT));

        assertThat(response.getContentAsString()).doesNotContain(ErrorProbeController.LEAKY_MESSAGE);
        // Correlação do Boot no padrão de log: [traceId-spanId] na própria linha do erro.
        assertThat(logLine(output, "Erro inesperado em GET /api/v1/test-errors/boom"))
                .contains("[" + TRACE_ID + "-");
        assertThat(output.getOut()).containsSubsequence("Erro inesperado em GET /api/v1/test-errors/boom",
                "java.lang.IllegalStateException: " + ErrorProbeController.LEAKY_MESSAGE);
    }

    private JsonNode errorsOf(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("errors");
    }

    private static String logLine(CapturedOutput output, String marker) {
        return output.getOut().lines()
                .filter(line -> line.contains(marker))
                .reduce((first, last) -> last)
                .orElseThrow(() -> new AssertionError("linha de log não encontrada: " + marker));
    }

    private MockHttpServletResponse perform(String method, String path, String contentType, String body,
            Map<String, String> headers) throws Exception {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), URI.create(path));
        if (contentType != null) {
            request.contentType(contentType);
        }
        if (body != null) {
            request.content(body);
        }
        headers.forEach(request::header);
        return mvc.perform(request).andReturn().getResponse();
    }

    /** Controller só de teste: dispara cada classe de erro sob o prefixo /api/v1. */
    @RestController
    @RequestMapping("/test-errors")
    static class ErrorProbeController {

        /** Mensagem com "isca": nada disso pode aparecer no corpo do 500. */
        static final String LEAKY_MESSAGE =
                "SQL falhou: select * from segredo at br.com.fintech.Dao (java.sql.SQLException)";

        record Payload(@NotBlank String name, @NotBlank @Email String email) {
        }

        record Query(Integer age) {
        }

        @ResponseStatus(HttpStatus.NOT_FOUND)
        static class ThingNotFoundException extends RuntimeException {
        }

        @GetMapping("/ok")
        String ok() {
            return "ok";
        }

        @PostMapping(path = "/payload", consumes = MediaType.APPLICATION_JSON_VALUE)
        Payload payload(@Valid @RequestBody Payload payload) {
            return payload;
        }

        @GetMapping("/params")
        String params(@RequestParam("n") @Min(1) int n) {
            return "ok";
        }

        @GetMapping("/model")
        String model(@Valid Query query) {
            return "ok";
        }

        @GetMapping("/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        String adminOnly() {
            return "ok";
        }

        @GetMapping("/missing")
        String missing() {
            throw new ThingNotFoundException();
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException(LEAKY_MESSAGE);
        }
    }
}
