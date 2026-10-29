package br.com.fintech.config;

import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

/** Locale fixo pt-BR (ADR-0004): mensagens de erro e de Bean Validation não seguem o Accept-Language. */
@Configuration(proxyBeanMethods = false)
public class LocaleConfig {

    public static final Locale PT_BR = Locale.of("pt", "BR");

    @Bean
    LocaleResolver localeResolver() {
        return new FixedLocaleResolver(PT_BR);
    }
}
