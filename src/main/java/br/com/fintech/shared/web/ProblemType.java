package br.com.fintech.shared.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Catálogo de {@code type} dos erros (ADR-0004). O slug nunca muda de significado; slug novo
 * entra aqui, em {@code messages_pt_BR.properties} e no {@code docs/arquitetura/api-contract.md}.
 */
public enum ProblemType {

    BAD_REQUEST("bad-request", HttpStatus.BAD_REQUEST),
    VALIDATION("validation-error", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN),
    NOT_FOUND("not-found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL("internal-error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_TOKEN("invalid-token", HttpStatus.BAD_REQUEST);

    public static final String URN_PREFIX = "urn:nexa-finance:problem:";

    private final String slug;
    private final HttpStatus status;

    ProblemType(String slug, HttpStatus status) {
        this.slug = slug;
        this.status = status;
    }

    public String slug() {
        return slug;
    }

    public HttpStatus status() {
        return status;
    }

    public URI uri() {
        return URI.create(URN_PREFIX + slug);
    }

    /**
     * Tipo de um erro do framework a partir do status. Status sem slug próprio cai na família:
     * 4xx vira {@code bad-request} e 5xx vira {@code internal-error}, mantendo o status real.
     * Os slugs de negócio (validação, token inválido) não passam por aqui: dividem o status 400
     * com {@code bad-request} e só saem de um handler que sabe o motivo.
     */
    public static ProblemType forStatus(HttpStatusCode status) {
        for (ProblemType type : values()) {
            if (type != VALIDATION && type != INVALID_TOKEN && type.status.value() == status.value()) {
                return type;
            }
        }
        return status.is5xxServerError() ? INTERNAL : BAD_REQUEST;
    }
}
