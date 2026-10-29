package br.com.fintech.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class RequiredConfigurationTest {

    private final RequiredConfiguration check = new RequiredConfiguration();

    @Test
    void passesWhenAllRequiredVariablesArePresent() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://localhost:5432/nexa")
                .withProperty("DB_USERNAME", "nexa")
                .withProperty("DB_PASSWORD", "s3nh4");

        assertThatCode(() -> check.initialize(contextWith(env))).doesNotThrowAnyException();
    }

    @Test
    void listsEveryMissingOrBlankVariableAndNeverItsValues() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DB_URL", "   ")
                .withProperty("DB_PASSWORD", "valor-que-nao-pode-vazar");

        assertThatThrownBy(() -> check.initialize(contextWith(env)))
                .isInstanceOf(MissingConfigurationException.class)
                .hasMessage("Configuração obrigatória ausente: DB_URL, DB_USERNAME")
                .satisfies(e -> org.assertj.core.api.Assertions
                        .assertThat(((MissingConfigurationException) e).getVariables())
                        .containsExactly("DB_URL", "DB_USERNAME"));
    }

    private static GenericApplicationContext contextWith(MockEnvironment env) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(env);
        return context;
    }
}
