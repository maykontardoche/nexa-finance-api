package br.com.fintech.auth.api;

import br.com.fintech.auth.application.InvalidTokenException;
import br.com.fintech.auth.application.PasswordRejectedException;
import br.com.fintech.config.LocaleConfig;
import br.com.fintech.shared.web.ProblemFactory;
import br.com.fintech.shared.web.ProblemFactory.FieldViolation;
import br.com.fintech.shared.web.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erros de negócio da identidade no contrato RFC 9457. Vem antes do handler geral, que tem um
 * catch-all de {@code Exception} e transformaria estes casos em 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    private final ProblemFactory problems;
    private final MessageSource messages;

    public AuthExceptionHandler(ProblemFactory problems, MessageSource messages) {
        this.problems = problems;
        this.messages = messages;
    }

    @ExceptionHandler(InvalidTokenException.class)
    ResponseEntity<ProblemDetail> handleInvalidToken(HttpServletRequest request) {
        ProblemType type = ProblemType.INVALID_TOKEN;
        return ResponseEntity.status(type.status()).body(problems.create(type, type.status(), request));
    }

    @ExceptionHandler(PasswordRejectedException.class)
    ResponseEntity<ProblemDetail> handlePasswordRejected(PasswordRejectedException ex, HttpServletRequest request) {
        String message = messages.getMessage(ex.violation().messageKey(), ex.violation().messageArguments(),
                LocaleConfig.PT_BR);
        return ResponseEntity.status(ProblemType.VALIDATION.status())
                .body(problems.validation(new FieldViolation(ex.field(), message), request));
    }
}
