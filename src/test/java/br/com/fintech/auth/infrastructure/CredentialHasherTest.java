package br.com.fintech.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CredentialHasherTest {

    private static final String PASSWORD = "uma frase longa e secreta";

    private final CredentialHasher hasher = new CredentialHasher();

    @Test
    void passwordHashIsArgon2idWithTheAdoptedParameters() {
        assertThat(hasher.hashPassword(PASSWORD)).startsWith("$argon2id$v=19$m=19456,t=4,p=1$");
    }

    @Test
    void hashMatchesTheOriginalPasswordAndNothingElse() {
        String hash = hasher.hashPassword(PASSWORD);

        assertThat(hasher.passwordMatches(PASSWORD, hash)).isTrue();
        assertThat(hasher.passwordMatches(PASSWORD + " ", hash)).isFalse();
        assertThat(hasher.passwordMatches("Uma frase longa e secreta", hash)).isFalse();
    }

    @Test
    void twoHashesOfTheSamePasswordDifferBecauseOfTheSalt() {
        String first = hasher.hashPassword(PASSWORD);
        String second = hasher.hashPassword(PASSWORD);

        assertThat(first).isNotEqualTo(second);
        assertThat(hasher.passwordMatches(PASSWORD, second)).isTrue();
    }

    @Test
    void composedAndDecomposedFormsOfTheSamePasswordMatch() {
        String composed = "senha do café da manhã";
        String decomposed = "senha do café da manhã";

        assertThat(hasher.passwordMatches(decomposed, hasher.hashPassword(composed))).isTrue();
    }

    @Test
    void sha256HexMatchesTheKnownVectorInTheColumnFormat() {
        // FIPS 180-2, apêndice B.1.
        assertThat(hasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(hasher.sha256Hex(hasher.newSecret())).matches("^[0-9a-f]{64}$");
    }

    @Test
    void newSecretIs32RandomBytesInUnpaddedBase64Url() {
        Set<String> secrets = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String secret = hasher.newSecret();
            assertThat(secret).hasSize(43).matches("^[A-Za-z0-9_-]+$");
            assertThat(Base64.getUrlDecoder().decode(secret)).hasSize(32);
            secrets.add(secret);
        }
        assertThat(secrets).hasSize(1_000);
    }
}
