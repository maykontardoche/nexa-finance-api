package br.com.fintech.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant CREATED = Instant.parse("2026-10-29T12:00:00Z");

    @Test
    void emailVerificationHappensOnceAndKeepsTheFirstDate() {
        User user = new User("Maria", "maria@example.com", "hash", CREATED);
        Instant first = CREATED.plusSeconds(60);

        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.verifyEmail(first)).isTrue();
        assertThat(user.verifyEmail(first.plusSeconds(60))).isFalse();

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isEqualTo(first);
        assertThat(user.getUpdatedAt()).isEqualTo(first);
    }
}
