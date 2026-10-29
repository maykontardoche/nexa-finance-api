package br.com.fintech.shared.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Substitui o {@code BasicErrorController} para erros que não passam pelo MVC (filtros,
 * container): mesmo corpo do {@link ApiExceptionHandler}, sem mensagem, exceção nem stack.
 */
@RestController
public class ProblemDetailsErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsErrorController.class);
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    private final ProblemFactory problems;
    private final Tracer tracer;

    public ProblemDetailsErrorController(ProblemFactory problems, Tracer tracer) {
        this.problems = problems;
        this.tracer = tracer;
    }

    @RequestMapping("${server.error.path:${error.path:/error}}")
    ResponseEntity<ProblemDetail> renderError(HttpServletRequest request, HttpServletResponse response) {
        HttpStatusCode status = statusOf(request);
        addMissingSecurityHeaders(response);
        ProblemDetail problem = problems.create(ProblemType.forStatus(status), status, request);
        if (status.is5xxServerError()) {
            logUnexpected(request, problem);
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /**
     * O {@code HeaderWriterFilter} do Spring Security não roda no dispatch de ERROR. Quando o erro
     * nasce antes da cadeia de segurança (firewall, filtro anterior), ninguém escreveu os headers
     * dela; completa os dois que importam para um corpo JSON, sem duplicar os que já existem.
     */
    private static void addMissingSecurityHeaders(HttpServletResponse response) {
        if (!response.containsHeader(X_CONTENT_TYPE_OPTIONS)) {
            response.setHeader(X_CONTENT_TYPE_OPTIONS, "nosniff");
        }
        if (!response.containsHeader(HttpHeaders.CACHE_CONTROL)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        }
    }

    /**
     * Neste dispatch o filtro de observação não roda de novo, então o MDC não tem a correlação da
     * requisição. O log roda no escopo do span da requisição para o {@code [traceId-spanId]} sair
     * certo; o {@code traceId=} na mensagem fica como redundância legível.
     */
    private void logUnexpected(HttpServletRequest request, ProblemDetail problem) {
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Span span = problems.requestSpan(request);
        try (Tracer.SpanInScope scope = span == null ? null : tracer.withSpan(span)) {
            log.error("Erro fora do MVC em {} (traceId={})", problem.getInstance(),
                    problem.getProperties() == null ? null : problem.getProperties().get("traceId"),
                    exception instanceof Throwable throwable ? throwable : null);
        }
    }

    private static HttpStatusCode statusOf(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return code instanceof Integer value ? HttpStatusCode.valueOf(value) : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
