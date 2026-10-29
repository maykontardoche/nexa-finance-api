package br.com.fintech.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fintech.NexaFinanceApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sem DB_URL, o startup real (o {@code main}) falha nomeando a variável e sem ecoar valores.
 *
 * <p>Roda em subprocesso para controlar o ambiente de verdade: no mesmo processo não dá para
 * remover uma DB_URL do sistema operacional nem evitar o .env da raiz do projeto. O diretório de
 * trabalho é uma pasta vazia, então não existe .env para importar.
 */
class MissingConfigurationStartupTest {

    /** Valor fictício: o teste verifica justamente que ele nunca aparece na saída. */
    private static final String PASSWORD = "senha-que-nao-pode-vazar-7f3a"; // gitleaks:allow
    private static final String USERNAME = "usuario-que-nao-pode-vazar-91c2";
    /** Folga para a JVM filha subir e abortar; sem a checagem ela ficaria no ar indefinidamente. */
    private static final long STARTUP_TIMEOUT_SECONDS = 60;

    @TempDir
    Path workDir;

    @Test
    void startupWithoutDbUrlFailsNamingOnlyTheVariable() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                NexaFinanceApplication.class.getName()))
                .directory(workDir.toFile())
                .redirectErrorStream(true);
        Path output = workDir.resolve("saida.log");
        builder.redirectOutput(output.toFile());
        Map<String, String> env = builder.environment();
        env.keySet().removeIf(name -> name.startsWith("DB_") || name.startsWith("SPRING_"));
        env.put("DB_USERNAME", USERNAME);
        env.put("DB_PASSWORD", PASSWORD);

        Process process = builder.start();
        boolean finished = process.waitFor(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly().waitFor();
        }

        assertThat(finished).as("a aplicação deveria abortar, não ficar no ar").isTrue();
        assertThat(process.exitValue()).isNotZero();
        assertThat(Files.readString(output, StandardCharsets.UTF_8))
                .contains("APPLICATION FAILED TO START")
                .contains("Variável de ambiente obrigatória ausente ou vazia: DB_URL.")
                .contains(".env.example")
                .doesNotContain(PASSWORD)
                .doesNotContain(USERNAME);
    }
}
