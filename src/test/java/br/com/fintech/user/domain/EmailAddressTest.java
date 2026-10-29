package br.com.fintech.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailAddressTest {

    @Test
    void emailIsNormalizedToTheStoredForm() {
        assertThat(EmailAddress.normalize("maria@example.com")).isEqualTo("maria@example.com");
        assertThat(EmailAddress.normalize("  Maria@Example.COM  ")).isEqualTo("maria@example.com");
        assertThat(EmailAddress.normalize("\tMARIA@EXAMPLE.COM\n")).isEqualTo("maria@example.com");
        assertThat(EmailAddress.normalize("maria.silva+financas@example.com"))
                .isEqualTo("maria.silva+financas@example.com");
    }
}
