package br.com.fintech;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Banco dos testes de integração: Postgres do Testcontainers como bean, com ciclo de vida
 * do contexto. Testes com a mesma configuração reaproveitam o contexto em cache e o container;
 * configurações diferentes (ex.: MOCK e RANDOM_PORT) sobem um container cada.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Senha fictícia e conhecida: os testes de redaction verificam que ela nunca vai para o log. */
    public static final String DB_PASSWORD = "senha-db-de-teste-5b1e"; // gitleaks:allow

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(TestDatabase.IMAGE).withPassword(DB_PASSWORD);
    }
}
