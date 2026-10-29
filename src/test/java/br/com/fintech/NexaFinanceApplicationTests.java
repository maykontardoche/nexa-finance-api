package br.com.fintech;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NexaFinanceApplicationTests {

    @Autowired
    PostgreSQLContainer postgres;

    @Autowired
    Flyway flyway;

    @Autowired
    DataSource dataSource;

    @Test
    void contextLoadsAndFlywayHasNoPendingMigrations() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.getConfiguration().getLocations())
                .extracting(Object::toString)
                .containsExactly("classpath:db/migration");
    }

    @Test
    void dataSourcePointsToPostgres18FromTestcontainers() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
            assertThat(connection.getMetaData().getURL()).isEqualTo(postgres.getJdbcUrl());
        }
    }
}
