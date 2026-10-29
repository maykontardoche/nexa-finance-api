package br.com.fintech.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.LinkedHashSet;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documento OpenAPI: metadados e o corpo de erro padrão (ADR-0004) como schema
 * {@value #PROBLEM_SCHEMA}. O schema é escrito à mão porque o {@code ProblemDetail} do Spring
 * serializa as extensões ({@code traceId}, {@code errors}) achatadas, e o gerado pela reflexão
 * mostraria um mapa {@code properties} que não existe no JSON.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String PROBLEM_SCHEMA = "ProblemDetail";
    static final String PROBLEM_JSON = "application/problem+json";
    private static final String PROBLEM_REF = "#/components/schemas/" + PROBLEM_SCHEMA;
    private static final String UNAUTHORIZED = "401";
    private static final String INTERNAL_ERROR = "500";

    @Bean
    OpenAPI nexaFinanceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nexa Finance API")
                        .version("v1")
                        .description("API de controle financeiro pessoal. Projeto de portfólio e acadêmico: "
                                + "não é um produto financeiro real. Toda rota fora da lista pública exige "
                                + "autenticação, e todo erro é application/problem+json (RFC 9457) com "
                                + "type urn:nexa-finance:problem:<slug> e textos em pt-BR."))
                .components(new Components().addSchemas(PROBLEM_SCHEMA, problemSchema()));
    }

    /**
     * Toda operação documenta o 401 (negação por padrão, ADR-0005) e o 500 com o corpo padrão,
     * sem sobrescrever o que o controller já declarou.
     */
    @Bean
    OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().stream()
                        .flatMap(path -> path.readOperations().stream())
                        .forEach(OpenApiConfig::addStandardErrorResponses);
            }
        };
    }

    private static void addStandardErrorResponses(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        operation.getResponses().putIfAbsent(UNAUTHORIZED, problemResponse("Sem credencial válida (unauthorized)."));
        operation.getResponses().putIfAbsent(INTERNAL_ERROR, problemResponse("Erro inesperado (internal-error)."));
    }

    private static ApiResponse problemResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(PROBLEM_JSON,
                        new MediaType().schema(new Schema<>().$ref(PROBLEM_REF))));
    }

    private static Schema<?> problemSchema() {
        Schema<?> violation = new ObjectSchema()
                .addProperty("field", nullable(new StringSchema()
                        .description("Campo ou parâmetro; nulo quando o erro é do objeto inteiro.")))
                .addProperty("message", new StringSchema().description("Mensagem em pt-BR."))
                .required(List.of("field", "message"));
        return new ObjectSchema()
                .description("Erro no formato RFC 9457 (ADR-0004). O cliente decide pelo type, nunca pelo texto.")
                .addProperty("type", new StringSchema().format("uri")
                        .description("Identificador estável da classe do erro: urn:nexa-finance:problem:<slug>.")
                        .example("urn:nexa-finance:problem:validation-error"))
                .addProperty("title", new StringSchema().description("Resumo em pt-BR da classe do erro.")
                        .example("Requisição inválida"))
                .addProperty("status", new IntegerSchema().format("int32").description("Status HTTP.")
                        .example(400))
                .addProperty("detail", new StringSchema().description("Explicação em pt-BR desta ocorrência.")
                        .example("Um ou mais campos são inválidos."))
                .addProperty("instance", new StringSchema().format("uri-reference")
                        .description("Caminho da requisição que falhou.").example("/api/v1/contas"))
                .addProperty("traceId", nullable(new StringSchema().pattern("^[0-9a-f]{32}$")
                        .description("Trace W3C da requisição; informe ao suporte para achar o log.")))
                .addProperty("errors", new ArraySchema().items(violation)
                        .description("Só em validation-error: todas as violações, ordenadas por field."))
                .required(List.of("type", "title", "status", "detail", "instance"));
    }

    /** OpenAPI 3.1: nulidade é o tipo {@code null} na lista de tipos. */
    private static Schema<?> nullable(Schema<?> schema) {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        types.add(schema.getType());
        types.add("null");
        return schema.types(types);
    }
}
