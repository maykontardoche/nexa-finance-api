package br.com.fintech.shared.web;

import br.com.fintech.config.LocaleConfig;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.filter.ServerHttpObservationFilter;

/**
 * Único ponto que monta o corpo de erro (ADR-0004): {@code type} do catálogo, {@code title} e
 * {@code detail} em pt-BR, {@code instance} com o caminho da requisição e {@code traceId}.
 */
@Component
public class ProblemFactory {

    static final String TRACE_ID = "traceId";
    static final String ERRORS = "errors";

    private final MessageSource messages;
    private final Tracer tracer;

    public ProblemFactory(MessageSource messages, Tracer tracer) {
        this.messages = messages;
        this.tracer = tracer;
    }

    public ProblemDetail create(ProblemType type, HttpStatusCode status, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(type.uri());
        problem.setTitle(message("problemDetail.title." + type.slug()));
        problem.setDetail(message("problemDetail.detail." + type.slug()));
        problem.setInstance(URI.create(originalPath(request)));
        problem.setProperty(TRACE_ID, traceId(request));
        return problem;
    }

    /** {@code validation-error} de um objeto (corpo JSON ou {@code @ModelAttribute}). */
    public ProblemDetail validation(BindingResult result, HttpServletRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        for (FieldError error : result.getFieldErrors()) {
            // Falha de conversão traz a mensagem do conversor, com nomes de classe.
            errors.add(violation(error.getField(), error.isBindingFailure() ? null : error.getDefaultMessage()));
        }
        result.getGlobalErrors().forEach(error ->
                errors.add(violation(null, error.getDefaultMessage())));
        return validationProblem(errors, request);
    }

    /** {@code validation-error} de parâmetros do método ({@code @RequestParam @Min(1)}…). */
    public ProblemDetail validation(List<ParameterValidationResult> results, HttpServletRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        for (ParameterValidationResult result : results) {
            if (result instanceof ParameterErrors bean) {
                bean.getFieldErrors().forEach(error -> errors.add(violation(error.getField(),
                        error.isBindingFailure() ? null : error.getDefaultMessage())));
                bean.getGlobalErrors().forEach(error -> errors.add(violation(null, error.getDefaultMessage())));
            } else {
                String name = parameterName(result.getMethodParameter());
                result.getResolvableErrors().forEach(error -> errors.add(violation(name, error.getDefaultMessage())));
            }
        }
        return validationProblem(errors, request);
    }

    /** {@code validation-error} de uma regra de negócio sobre um campo (ex.: senha recusada). */
    public ProblemDetail validation(FieldViolation violation, HttpServletRequest request) {
        return validationProblem(new ArrayList<>(List.of(violation)), request);
    }

    private ProblemDetail validationProblem(List<FieldViolation> errors, HttpServletRequest request) {
        ProblemDetail problem = create(ProblemType.VALIDATION, ProblemType.VALIDATION.status(), request);
        errors.sort(Comparator.comparing(FieldViolation::field, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(FieldViolation::message));
        problem.setProperty(ERRORS, errors);
        return problem;
    }

    /** Sem mensagem própria (ou com mensagem do conversor), o cliente recebe o texto genérico pt-BR. */
    private FieldViolation violation(@Nullable String field, @Nullable String message) {
        return new FieldViolation(field, message == null ? message("problemDetail.field.invalid-value") : message);
    }

    /** Nome que o cliente enviou (o da anotação), não o nome Java do parâmetro. */
    private static String parameterName(MethodParameter parameter) {
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null && !param.name().isEmpty()) {
            return param.name();
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null && !path.name().isEmpty()) {
            return path.name();
        }
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.name().isEmpty()) {
            return header.name();
        }
        String name = parameter.getParameterName();
        return name == null ? "arg" + parameter.getParameterIndex() : name;
    }

    private String message(String code) {
        return messages.getMessage(code, null, LocaleConfig.PT_BR);
    }

    /**
     * Span da requisição HTTP: o criado pelo filtro de observação, guardado na própria requisição.
     * Ele vem antes do span corrente porque, no dispatch de ERROR (falha num filtro), o filtro de
     * observação não roda de novo e o span corrente não é o da requisição. O span corrente fica
     * como fallback (fora de requisição HTTP).
     */
    public @Nullable Span requestSpan(HttpServletRequest request) {
        return ServerHttpObservationFilter.findObservationContext(request)
                .map(context -> context.<TracingContext>get(TracingContext.class))
                .map(TracingContext::getSpan)
                .orElseGet(tracer::currentSpan);
    }

    private @Nullable String traceId(HttpServletRequest request) {
        Span span = requestSpan(request);
        if (span == null) {
            return null;
        }
        String traceId = span.context().traceId();
        return traceId.isBlank() ? null : traceId;
    }

    /** No encaminhamento para /error, o caminho original fica num atributo do container. */
    private static String originalPath(HttpServletRequest request) {
        Object forwarded = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return forwarded instanceof String path ? path : request.getRequestURI();
    }

    /**
     * Item de {@code errors[]}: campo e mensagem pt-BR. Erro do objeto inteiro (sem campo) vai com
     * {@code field} nulo: o nome do objeto vem do tipo Java e não diz nada ao cliente.
     */
    public record FieldViolation(@Nullable String field, String message) {
    }
}
