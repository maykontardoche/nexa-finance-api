package br.com.fintech.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guarda estrutural do ADR-0005, que o teste comportamental não cobre. O teste da lista pública fixa
 * {@code PUBLIC_PATHS}, mas um {@code permitAll()} escrito direto na cadeia ou um
 * {@code web.ignoring()} passaria por fora dela. Aqui o código de produção é lido como texto
 * (comentários ignorados): qualquer liberação nova falha o build até ser revista.
 */
class SecurityGuardrailsTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");
    private static final Path SECURITY_CONFIG =
            MAIN_SOURCES.resolve(Path.of("br", "com", "fintech", "config", "SecurityConfig.java"));

    @Test
    void permitAllExistsOnlyInSecurityConfigForErrorDispatchAndPublicPaths() throws IOException {
        // 3 = dispatch de ERROR + GET e HEAD em PUBLIC_PATHS. Mudou? Atualize o ADR e o teste da lista pública junto.
        assertThat(occurrences("permitAll(")).isEqualTo(Map.of(SECURITY_CONFIG, 3));
    }

    @Test
    void noRequestBypassesTheSecurityFilterChain() throws IOException {
        assertThat(occurrences("ignoring(")).isEmpty();
        assertThat(occurrences("WebSecurityCustomizer")).isEmpty();
    }

    /** Ocorrências de {@code token} fora de comentários, por arquivo de produção. */
    private static Map<Path, Integer> occurrences(String token) throws IOException {
        Map<Path, Integer> found = new TreeMap<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                int count = 0;
                for (String line : Files.readAllLines(file)) {
                    String code = line.strip();
                    if (code.startsWith("*") || code.startsWith("/*") || code.startsWith("//")) {
                        continue;
                    }
                    count += countIn(code, token);
                }
                if (count > 0) {
                    found.put(file, count);
                }
            }
        }
        return found;
    }

    private static int countIn(String text, String token) {
        int count = 0;
        for (int index = text.indexOf(token); index >= 0; index = text.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }
}
