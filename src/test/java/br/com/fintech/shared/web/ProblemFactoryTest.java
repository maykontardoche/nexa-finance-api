package br.com.fintech.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fintech.config.LocaleConfig;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.RequestDispatcher;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

class ProblemFactoryTest {

    private final ProblemFactory factory = new ProblemFactory(messages(), Tracer.NOOP);

    @ParameterizedTest
    @EnumSource(ProblemType.class)
    void everyTypeHasPortugueseTitleAndDetail(ProblemType type) {
        // Sem fallback: chave ausente no bundle derruba o teste (NoSuchMessageException).
        ProblemDetail problem = factory.create(type, type.status(), request("/api/v1/x"));

        assertThat(problem.getType()).isEqualTo(type.uri());
        assertThat(problem.getStatus()).isEqualTo(type.status().value());
        assertThat(problem.getTitle()).isNotBlank();
        assertThat(problem.getDetail()).isNotBlank();
    }

    @Test
    void instanceIsTheRequestPath() {
        ProblemDetail problem = factory.create(ProblemType.NOT_FOUND, HttpStatus.NOT_FOUND,
                request("/api/v1/contas/42"));

        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/contas/42"));
    }

    @Test
    void instanceIsTheOriginalPathWhenForwardedToErrorPage() {
        MockHttpServletRequest forwarded = request("/error");
        forwarded.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/original");

        ProblemDetail problem = factory.create(ProblemType.INTERNAL, HttpStatus.INTERNAL_SERVER_ERROR, forwarded);

        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/original"));
    }

    @Test
    void keepsTheRealStatusWhenTheTypeIsAFamilyFallback() {
        ProblemDetail problem = factory.create(ProblemType.BAD_REQUEST, HttpStatus.NOT_ACCEPTABLE,
                request("/api/v1/x"));

        assertThat(problem.getStatus()).isEqualTo(406);
        assertThat(problem.getType()).isEqualTo(ProblemType.BAD_REQUEST.uri());
    }

    @Test
    void errorsOnlyAppearInValidation() {
        ProblemDetail plain = factory.create(ProblemType.BAD_REQUEST, HttpStatus.BAD_REQUEST, request("/x"));

        assertThat(plain.getProperties()).doesNotContainKey(ProblemFactory.ERRORS);
        // Sem span (o NOOP devolve trace vazio) o traceId é nulo, nunca "". Em requisição HTTP o
        // teste de integração garante valor.
        assertThat(plain.getProperties()).containsEntry(ProblemFactory.TRACE_ID, null);
    }

    @Test
    void validationListsFieldAndGlobalErrorsSortedWithObjectErrorsFirst() {
        BindingResult result = new BeanPropertyBindingResult(new Object(), "payload");
        result.rejectValue(null, "Global", "datas incoerentes");
        result.addError(new FieldError("payload", "zeta", "inválido"));
        result.addError(new FieldError("payload", "alfa", "obrigatório"));

        ProblemDetail problem = factory.validation(result, request("/api/v1/x"));

        assertThat(problem.getType()).isEqualTo(ProblemType.VALIDATION.uri());
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties()).containsEntry(ProblemFactory.ERRORS, List.of(
                new ProblemFactory.FieldViolation(null, "datas incoerentes"),
                new ProblemFactory.FieldViolation("alfa", "obrigatório"),
                new ProblemFactory.FieldViolation("zeta", "inválido")));
    }

    @Test
    void bindingFailureNeverExposesTheConverterMessage() {
        BindingResult result = new BeanPropertyBindingResult(new Object(), "payload");
        result.addError(new FieldError("payload", "age", "abc", true, null, null,
                "Failed to convert value of type 'java.lang.String' to required type 'java.lang.Integer'"));

        ProblemDetail problem = factory.validation(result, request("/api/v1/x"));

        assertThat(problem.getProperties()).containsEntry(ProblemFactory.ERRORS,
                List.of(new ProblemFactory.FieldViolation("age", "valor inválido")));
    }

    private static MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        assertThat(source.getMessage("problemDetail.title.not-found", null, LocaleConfig.PT_BR))
                .isEqualTo("Recurso não encontrado");
        return source;
    }
}
