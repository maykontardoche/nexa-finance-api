package br.com.fintech.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class EmailTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    @ParameterizedTest
    @CsvSource({"EMAIL_VERIFICATION, PT24H", "EMAIL_CHANGE, PT24H", "PASSWORD_RESET, PT30M"})
    void issuedTokenExpiresAfterThePurposeValidity(EmailTokenPurpose purpose, Duration validity) {
        EmailToken token = EmailToken.issue(UUID.randomUUID(), purpose, "a".repeat(64), null, NOW);

        assertThat(token.getCreatedAt()).isEqualTo(NOW);
        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(validity));
        assertThat(token.getUsedAt()).isNull();
    }

    @Test
    void tokenIsUsableUntilJustBeforeExpiry() {
        EmailToken token = EmailToken.issue(UUID.randomUUID(), EmailTokenPurpose.EMAIL_VERIFICATION, "a".repeat(64),
                null, NOW);
        Instant expiry = NOW.plus(Duration.ofHours(24));

        assertThat(token.isUsableAt(NOW)).isTrue();
        assertThat(token.isUsableAt(expiry.minusNanos(1))).isTrue();
        // O instante exato da expiração já não vale, como no "expires_at > now" do consumo no banco.
        assertThat(token.isUsableAt(expiry)).isFalse();
        assertThat(token.isUsableAt(expiry.plusSeconds(1))).isFalse();
    }
}
