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
 * todo o resto, inclusive rota inexistente, recebe 401 no formato do ADR-0004. Ainda não há
 * mecanismo de login (Fase 2), então nenhuma requisição real consegue se autenticar.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Rotas públicas, só para leitura (GET e HEAD, que o healthcheck de contêiner usa). Rota nova
     * aqui exige atualizar o teste que fixa a lista; {@code permitAll()} fora desta lista é
     * proibido.
     */
    public static final List<String> PUBLIC_PATHS =
            List.of("/actuator/health", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**");

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemDetailsSecurityHandlers problems) throws Exception {
        http
                // API stateless, sem cookie de sessão: CSRF não se aplica. Reavaliar na Fase 2 se a
                // autenticação usar cookie.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .authorizeHttpRequests(auth -> auth
                        // Só o encaminhamento interno do container para renderizar o erro; um
                        // GET /error direto é REQUEST e cai no authenticated() abaixo.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_PATHS.toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.HEAD, PUBLIC_PATHS.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
