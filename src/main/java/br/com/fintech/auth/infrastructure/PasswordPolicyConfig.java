package br.com.fintech.auth.infrastructure;

import br.com.fintech.auth.domain.PasswordPolicy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/** Monta a política de senha com a lista de senhas comuns empacotada no jar. */
@Configuration(proxyBeanMethods = false)
public class PasswordPolicyConfig {

    static final String COMMON_PASSWORDS = "security/common-passwords.txt";

    @Bean
    PasswordPolicy passwordPolicy() {
        return new PasswordPolicy(readCommonPasswords(new ClassPathResource(COMMON_PASSWORDS)));
    }

    /** Uma senha por linha; linhas em branco e as que começam com "#" (atribuição) são ignoradas. */
    static List<String> readCommonPasswords(Resource resource) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> passwords = reader.lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList();
            if (passwords.isEmpty()) {
                // Lista vazia desligaria a regra em silêncio: melhor não subir.
                throw new IllegalStateException("lista de senhas comuns vazia: " + resource.getDescription());
            }
            return passwords;
        } catch (IOException e) {
            throw new UncheckedIOException("não foi possível ler a lista de senhas comuns", e);
        }
    }
}
