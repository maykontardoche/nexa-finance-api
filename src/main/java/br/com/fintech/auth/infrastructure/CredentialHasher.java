package br.com.fintech.auth.infrastructure;

import br.com.fintech.auth.domain.PasswordPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hash de credenciais. Senha: Argon2id lento (ADR-0009). Segredo de renovação e token de e-mail:
 * SHA-256, que basta para 256 bits aleatórios e permite buscar a linha pelo hash.
 */
@Component
public class CredentialHasher {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    // Custo (ADR-0009): a memória do mínimo da OWASP com o dobro de iterações. O padrão do
    // Spring (16 MiB) fica abaixo desse mínimo.
    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 4;

    private static final int SECRET_BYTES = 32;

    private final Argon2PasswordEncoder passwordEncoder =
            new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS);
    private final SecureRandom random = new SecureRandom();

    /** Hash Argon2id da senha normalizada (NFKC), com sal e parâmetros embutidos. */
    public String hashPassword(String password) {
        String hash = passwordEncoder.encode(PasswordPolicy.normalize(password));
        if (hash == null) {
            throw new IllegalStateException("o encoder não gerou o hash da senha");
        }
        return hash;
    }

    public boolean passwordMatches(String password, String passwordHash) {
        return passwordEncoder.matches(PasswordPolicy.normalize(password), passwordHash);
    }

    /** SHA-256 em hex minúsculo, o formato das colunas {@code refresh_hash} e {@code token_hash}. */
    public String sha256Hex(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Todo JDK é obrigado a ter SHA-256 (MessageDigest, "Implementation Requirements").
            throw new IllegalStateException(e);
        }
    }

    /** 32 bytes aleatórios em base64url sem padding: segredo de renovação e token de e-mail. */
    public String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
