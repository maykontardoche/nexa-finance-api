package br.com.fintech.config;

import java.util.function.Predicate;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Todo {@code @RestController} do projeto responde sob {@code /api/v1}. Actuator e
 * springdoc ficam fora do pacote base e não recebem o prefixo; o error controller também não,
 * porque o container encaminha erros para {@code /error}.
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersioningConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api/v1";

    static final Predicate<Class<?>> VERSIONED_CONTROLLERS = HandlerTypePredicate
            .forBasePackage("br.com.fintech")
            .and(HandlerTypePredicate.forAnnotation(RestController.class))
            .and(type -> !ErrorController.class.isAssignableFrom(type));

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, VERSIONED_CONTROLLERS);
    }
}
