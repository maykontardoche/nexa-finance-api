package br.com.fintech.config;

import java.util.List;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Recusa o startup quando falta variável obrigatória.
 *
 * <p>Necessário porque o Boot deixa {@code ${DB_URL}} sem valor como texto literal, e a falha
 * só aparece depois, no Hikari, sem citar a variável. Roda antes do refresh, então nenhum bean
 * chega a tentar conectar.
 */
public final class RequiredConfiguration
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final List<String> REQUIRED = List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        List<String> missing = REQUIRED.stream()
                .filter(name -> isBlank(environment.getProperty(name)))
                .toList();
        if (!missing.isEmpty()) {
            throw new MissingConfigurationException(missing);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
