package br.com.fintech.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte todo erro do MVC no corpo padrão. Os erros que o Spring já conhece
 * (400, 404, 405, 415…) passam por {@link #handleExceptionInternal}; o resto vira
 * {@code internal-error} com detalhe genérico e a stack só no log.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ProblemFactory problems;

    public ApiExceptionHandler(ProblemFactory problems) {
        this.problems = problems;
    }

    /**
     * Exceção sem handler próprio. Se a classe declara {@code @ResponseStatus}, o status dela vale
     * (o catch-all não pode transformar um 404 do domínio em 500); senão, {@code internal-error}.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        ResponseStatus declared = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        HttpStatusCode status = declared != null ? declared.code() : ProblemType.INTERNAL.status();
        return handleExceptionInternal(ex, null, new HttpHeaders(), status, request);
    }

    /** Sem credencial válida (vem do {@code ProblemDetailsSecurityHandlers}). */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return handleExceptionInternal(ex, null, new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
    }

    /**
     * Sem permissão: do filtro de segurança ou de um {@code @PreAuthorize} dentro do MVC, que
     * sem este handler cairia no catch-all como 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return handleExceptionInternal(ex, null, new HttpHeaders(), HttpStatus.FORBIDDEN, request);
    }

    /** Validação de parâmetro ({@code @RequestParam @Min(1)}…) também é {@code validation-error}. */
    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            // Falha de validação do valor de retorno é bug do servidor, não entrada do cliente.
            return handleExceptionInternal(ex, null, headers, status, request);
        }
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        ProblemDetail problem = problems.validation(ex.getParameterValidationResults(), servletRequest);
        return super.handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        ProblemDetail problem = ex instanceof MethodArgumentNotValidException invalid
                ? problems.validation(invalid.getBindingResult(), servletRequest)
                : problems.create(ProblemType.forStatus(statusCode), statusCode, servletRequest);
        if (statusCode.is5xxServerError()) {
            log.error("Erro inesperado em {} {}", servletRequest.getMethod(),
                    servletRequest.getRequestURI(), ex);
        }
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }
}
