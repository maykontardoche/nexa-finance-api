package br.com.fintech.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fintech.config.LocaleConfig;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.ResourceBundleMessageSource;

class PasswordPolicyTest {

    private static final String EMAIL = "maria@example.com";

    private final PasswordPolicy policy = new PasswordPolicy(List.of("qwertyuiopasdfgh", "PasswordPassword"));

    static Stream<Arguments> passwords() {
        return Stream.of(
                // Limites de tamanho, em code points.
                Arguments.of("14 caracteres", text(14), PasswordViolation.TOO_SHORT),
                Arguments.of("15 caracteres", text(15), null),
                Arguments.of("128 caracteres", text(128), null),
                Arguments.of("129 caracteres", text(129), PasswordViolation.TOO_LONG),
                Arguments.of("vazia", "", PasswordViolation.TOO_SHORT),
                // Fora do BMP, cada caractere ocupa duas unidades UTF-16 mas conta como um.
                Arguments.of("14 clave de sol (28 unidades UTF-16)", "𝄞".repeat(14), PasswordViolation.TOO_SHORT),
                Arguments.of("15 clave de sol", "𝄞".repeat(15), null),
                // A ligadura "ﬁ" vira "fi" no NFKC: 14 caracteres digitados, 15 depois da normalização.
                Arguments.of("ligadura que completa o mínimo", "ﬁ" + text(13), null),
                // Qualquer caractere vale, sem regra de composição.
                Arguments.of("acentos, espaços e emoji", "minha senha é ótima 🎉 2026", null),
                Arguments.of("só espaços", " ".repeat(15), null),
                Arguments.of("só minúsculas", "abcdefghijklmnop", null),
                // Lista de senhas comuns, sem diferença de caixa nem de forma Unicode.
                Arguments.of("comum", "qwertyuiopasdfgh", PasswordViolation.COMMON),
                Arguments.of("comum em outra caixa", "QwertyUiopAsdfgh", PasswordViolation.COMMON),
                Arguments.of("comum em caracteres de largura total", "ｑｗｅｒｔｙｕｉｏｐａｓｄｆｇｈ", PasswordViolation.COMMON),
                Arguments.of("entrada da lista em maiúsculas", "passwordpassword", PasswordViolation.COMMON),
                Arguments.of("comum com um caractere a mais", "qwertyuiopasdfgh!", null),
                // E-mail contido, sem diferença de caixa.
                Arguments.of("contém o e-mail", "minha-" + EMAIL + "-segura", PasswordViolation.CONTAINS_EMAIL),
                Arguments.of("contém o e-mail em maiúsculas", "MARIA@EXAMPLE.COM e mais nada", PasswordViolation.CONTAINS_EMAIL),
                Arguments.of("contém só a parte local", "maria gosta de café forte", null),
                // Tamanho é checado antes do conteúdo.
                Arguments.of("comum e curta", "12345678", PasswordViolation.TOO_SHORT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("passwords")
    void checksThePassword(String description, String password, PasswordViolation expected) {
        assertThat(policy.check(password, EMAIL)).isEqualTo(Optional.ofNullable(expected));
    }

    @Test
    void emailIsComparedWithoutTheSurroundingSpaces() {
        assertThat(policy.check("minha-" + EMAIL + "-segura", "  " + EMAIL + " "))
                .contains(PasswordViolation.CONTAINS_EMAIL);
    }

    @Test
    void blankEmailIsRejectedInsteadOfRefusingEveryPassword() {
        assertThatThrownBy(() -> policy.check(text(20), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizationIsNfkc() {
        assertThat(PasswordPolicy.normalize("café")).isEqualTo("café");
        assertThat(PasswordPolicy.normalize("a b")).isEqualTo("a b");
        assertThat(PasswordPolicy.normalize("ﬁ")).isEqualTo("fi");
    }

    @ParameterizedTest
    @EnumSource(PasswordViolation.class)
    void everyViolationHasAPortugueseMessage(PasswordViolation violation) {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setUseCodeAsDefaultMessage(false);

        String text = messages.getMessage(violation.messageKey(), violation.messageArguments(), LocaleConfig.PT_BR);

        assertThat(text).isNotBlank().doesNotContain("{0}");
        switch (violation) {
            case TOO_SHORT -> assertThat(text).contains("15 caracteres");
            case TOO_LONG -> assertThat(text).contains("128 caracteres");
            case COMMON -> assertThat(text).contains("fácil de adivinhar");
            case CONTAINS_EMAIL -> assertThat(text).contains("e-mail");
        }
    }

    /** Texto de {@code length} caracteres ASCII que não está em nenhuma lista. */
    private static String text(int length) {
        return "Zk9 rio-azul ".repeat(length / 13 + 1).substring(0, length);
    }
}
