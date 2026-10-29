package br.com.fintech.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

/** O customizer vale para os endpoints das próximas fases; aqui ele roda sobre um documento sintético. */
class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void everyOperationDocumentsUnauthorizedAndInternalErrorAsProblemJson() {
        Operation operation = new Operation();
        OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem("/api/v1/contas", new PathItem().get(operation)));

        config.standardErrorResponses().customise(openApi);

        for (String status : new String[] {"401", "500"}) {
            ApiResponse response = operation.getResponses().get(status);
            assertThat(response.getContent().get(OpenApiConfig.PROBLEM_JSON).getSchema().get$ref())
                    .isEqualTo("#/components/schemas/" + OpenApiConfig.PROBLEM_SCHEMA);
        }
    }

    @Test
    void responsesDeclaredByTheControllerAreKept() {
        ApiResponse declared = new ApiResponse().description("declarado no controller");
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse("401", declared));
        OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem("/api/v1/x", new PathItem().post(operation)));

        config.standardErrorResponses().customise(openApi);

        assertThat(operation.getResponses().get("401")).isSameAs(declared);
        assertThat(operation.getResponses()).containsKey("500");
    }

    @Test
    void documentWithoutPathsIsLeftAlone() {
        OpenAPI openApi = new OpenAPI();

        config.standardErrorResponses().customise(openApi);

        assertThat(openApi.getPaths()).isNull();
    }

    @Test
    void problemSchemaRequiresTheRfc9457CoreFields() {
        OpenAPI openApi = config.nexaFinanceOpenApi();

        assertThat(openApi.getComponents().getSchemas().get(OpenApiConfig.PROBLEM_SCHEMA).getRequired())
                .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance");
    }
}
