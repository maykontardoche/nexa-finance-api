package br.com.fintech.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fintech.auth.domain.PasswordPolicy;
import br.com.fintech.auth.domain.PasswordViolation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class PasswordPolicyConfigTest {

    @Test
    void packagedListHasOnlyEntriesTheLengthRuleWouldAccept() {
        List<String> passwords =
                PasswordPolicyConfig.readCommonPasswords(new ClassPathResource(PasswordPolicyConfig.COMMON_PASSWORDS));

        assertThat(passwords).hasSizeGreaterThan(400).doesNotHaveDuplicates().contains("passwordpassword");
        assertThat(passwords).allSatisfy(password -> {
            assertThat(password).doesNotStartWith("#");
            assertThat(password.codePointCount(0, password.length()))
                    .isBetween(PasswordPolicy.MIN_LENGTH, PasswordPolicy.MAX_LENGTH);
        });
    }

    @Test
    void policyBeanRefusesAPasswordFromThePackagedList() {
        PasswordPolicy policy = new PasswordPolicyConfig().passwordPolicy();

        assertThat(policy.check("qwertyuiopasdfghjklzxcvbnm", "maria@example.com")).contains(PasswordViolation.COMMON);
        assertThat(policy.check("uma frase longa e secreta", "maria@example.com")).isEmpty();
    }

    @Test
    void commentsAndBlankLinesAreSkipped() {
        var resource = new ByteArrayResource("# atribuição\n\nsenha-comum-longa-1\n".getBytes(StandardCharsets.UTF_8));

        assertThat(PasswordPolicyConfig.readCommonPasswords(resource)).containsExactly("senha-comum-longa-1");
    }

    @Test
    void emptyListFailsInsteadOfSilentlyDisablingTheRule() {
        var resource = new ByteArrayResource("# só a atribuição\n\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> PasswordPolicyConfig.readCommonPasswords(resource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vazia");
    }
}
