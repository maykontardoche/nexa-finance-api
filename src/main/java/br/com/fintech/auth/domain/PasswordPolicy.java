package br.com.fintech.auth.domain;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Política de senha para senha como único fator (NIST SP 800-63B-4): de 15 a 128 caracteres,
 * qualquer caractere aceito, sem regra de composição. Recusa senha comum e senha que contém o
 * próprio e-mail.
 *
 * <p>A senha é comparada e contada depois da normalização NFKC, a mesma aplicada antes do hash:
 * assim, a mesma senha digitada com outra forma Unicode (acento composto ou decomposto, espaço
 * não separável) vale igual.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 15;
    public static final int MAX_LENGTH = 128;

    private final Set<String> commonPasswords;

    /** {@code commonPasswords} em qualquer caixa e forma Unicode: a comparação é normalizada. */
    public PasswordPolicy(Collection<String> commonPasswords) {
        this.commonPasswords = commonPasswords.stream()
                .map(PasswordPolicy::comparable)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Forma canônica da senha: a que é validada e a que é passada ao hash. */
    public static String normalize(String password) {
        return Normalizer.normalize(password, Normalizer.Form.NFKC);
    }

    /**
     * O primeiro motivo de recusa, ou vazio se a senha é aceita. Tamanho vem antes do conteúdo:
     * uma senha curta é recusada pelo tamanho, mesmo que também seja comum.
     *
     * @param email o e-mail da conta, já normalizado
     */
    public Optional<PasswordViolation> check(String password, String email) {
        if (email.isBlank()) {
            // Sem isso, contains("") recusaria toda senha como "contém o e-mail".
            throw new IllegalArgumentException("e-mail obrigatório para validar a senha");
        }
        String normalized = normalize(password);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_LENGTH) {
            return Optional.of(PasswordViolation.TOO_SHORT);
        }
        if (length > MAX_LENGTH) {
            return Optional.of(PasswordViolation.TOO_LONG);
        }
        String comparable = comparable(normalized);
        if (comparable.contains(comparable(email.strip()))) {
            return Optional.of(PasswordViolation.CONTAINS_EMAIL);
        }
        if (commonPasswords.contains(comparable)) {
            return Optional.of(PasswordViolation.COMMON);
        }
        return Optional.empty();
    }

    private static String comparable(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }
}
