package br.com.fintech;

import br.com.fintech.config.RequiredConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Sem o usuário em memória do Boot: nenhum autenticador existe até a Fase 2, e nenhuma senha
// gerada vai para o log.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class NexaFinanceApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(NexaFinanceApplication.class);
        // Só no main: nos testes a conexão vem do Testcontainers (@ServiceConnection), não do ambiente.
        application.addInitializers(new RequiredConfiguration());
        application.run(args);
    }
}
