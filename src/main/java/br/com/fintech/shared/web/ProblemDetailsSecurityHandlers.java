package br.com.fintech.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 401 e 403 dos filtros de segurança. Delegam ao {@link HandlerExceptionResolver} do MVC para que
 * o corpo saia do mesmo {@link ApiExceptionHandler} e do mesmo {@link ProblemFactory} dos outros
 * erros (formato, {@code traceId} e serialização idênticos).
 */
@Component
public class ProblemDetailsSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public ProblemDetailsSecurityHandlers(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        resolve(request, response, exception, HttpStatus.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        resolve(request, response, exception, HttpStatus.FORBIDDEN);
    }

    private void resolve(HttpServletRequest request, HttpServletResponse response, Exception exception,
            HttpStatus status) throws IOException {
        if (resolver.resolveException(request, response, null, exception) == null) {
            // Sem resolver não há corpo: o container encaminha para o error controller, que
            // devolve o mesmo formato.
            response.sendError(status.value());
        }
    }
}
