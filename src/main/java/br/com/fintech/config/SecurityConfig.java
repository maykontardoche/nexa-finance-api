package br.com.fintech.config;

import br.com.fintech.shared.web.ProblemDetailsSecurityHandlers;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Negação por padrão antes do roteamento (ADR-0005): só a lista pública responde sem credencial;
 * todo o resto, inclusive rota inexistente, recebe 401 no formato do ADR-0004.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Rotas públicas, por método e caminho: abrir um caminho não abre os outros métodos nele.
     * Rota nova aqui exige atualizar o teste que fixa a lista; {@code permitAll()} fora desta
     * lista é proibido.
     */
    public static final List<PublicRoute> PUBLIC_ROUTES = List.of(
            // Plataforma, só leitura (HEAD é o que o healthcheck do contêiner usa).
            new PublicRoute(HttpMethod.GET, "/actuator/health"),
            new PublicRoute(HttpMethod.HEAD, "/actuator/health"),
            new PublicRoute(HttpMethod.GET, "/v3/api-docs/**"),
            new PublicRoute(HttpMethod.HEAD, "/v3/api-docs/**"),
            new PublicRoute(HttpMethod.GET, "/swagger-ui.html"),
            new PublicRoute(HttpMethod.HEAD, "/swagger-ui.html"),
            new PublicRoute(HttpMethod.GET, "/swagger-ui/**"),
            new PublicRoute(HttpMethod.HEAD, "/swagger-ui/**"),
            // Cadastro e confirmação de e-mail: quem chama ainda não tem conta.
            new PublicRoute(HttpMethod.POST, "/api/v1/auth/register"),
            new PublicRoute(HttpMethod.GET, "/api/v1/auth/email-verification"),
            new PublicRoute(HttpMethod.POST, "/api/v1/auth/email-verification/resend"));

    /** Um método num padrão de caminho do Spring Security. */
    public record PublicRoute(HttpMethod method, String pattern) {
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemDetailsSecurityHandlers problems) throws Exception {
        http
                // API stateless, sem cookie de sessão: CSRF não se aplica. Reavaliar se a
                // autenticação passar a usar cookie.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .authorizeHttpRequests(auth -> {
                    // Só o encaminhamento interno do container para renderizar o erro; um
                    // GET /error direto é REQUEST e cai no authenticated() abaixo.
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    for (PublicRoute route : PUBLIC_ROUTES) {
                        auth.requestMatchers(route.method(), route.pattern()).permitAll();
                    }
                    auth.anyRequest().authenticated();
                });
        return http.build();
    }
}
