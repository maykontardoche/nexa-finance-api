package br.com.fintech.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Os handlers só delegam: o corpo é do {@code ApiExceptionHandler}, coberto pelos testes de
 * integração (401 no servidor real, 403 no MockMvc). Aqui fica a delegação e o fallback.
 */
class ProblemDetailsSecurityHandlersTest {

    private final List<Exception> resolved = new ArrayList<>();

    @Test
    void unauthorizedIsDelegatedToTheMvcResolver() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        InsufficientAuthenticationException exception = new InsufficientAuthenticationException("sem credencial");

        resolvingHandlers().commence(new MockHttpServletRequest(), response, exception);

        assertThat(resolved).containsExactly(exception);
        assertThat(response.isCommitted()).isFalse();
    }

    @Test
    void forbiddenIsDelegatedToTheMvcResolver() throws Exception {
        AccessDeniedException exception = new AccessDeniedException("negado");

        resolvingHandlers().handle(new MockHttpServletRequest(), new MockHttpServletResponse(), exception);

        assertThat(resolved).containsExactly(exception);
    }

    @Test
    void withoutResolverTheStatusGoesToTheErrorPage() throws Exception {
        MockHttpServletResponse unauthorized = new MockHttpServletResponse();
        MockHttpServletResponse forbidden = new MockHttpServletResponse();

        handlersWithoutResolver().commence(new MockHttpServletRequest(), unauthorized,
                new InsufficientAuthenticationException("sem credencial"));
        handlersWithoutResolver().handle(new MockHttpServletRequest(), forbidden, new AccessDeniedException("negado"));

        // sendError: o container encaminha para o ProblemDetailsErrorController.
        assertThat(unauthorized.getStatus()).isEqualTo(401);
        assertThat(unauthorized.isCommitted()).isTrue();
        assertThat(forbidden.getStatus()).isEqualTo(403);
    }

    private ProblemDetailsSecurityHandlers resolvingHandlers() {
        return new ProblemDetailsSecurityHandlers((request, response, handler, exception) -> {
            resolved.add(exception);
            return new ModelAndView();
        });
    }

    private ProblemDetailsSecurityHandlers handlersWithoutResolver() {
        HandlerExceptionResolver unresolved = (request, response, handler, exception) -> null;
        return new ProblemDetailsSecurityHandlers(unresolved);
    }
}
