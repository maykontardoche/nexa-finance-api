package br.com.fintech.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatusCode;

class ProblemTypeTest {

    @Test
    void slugsAreUniqueAndKebabCase() {
        assertThat(Arrays.stream(ProblemType.values()).map(ProblemType::slug))
                .doesNotHaveDuplicates()
                .allMatch(slug -> slug.matches("[a-z]+(-[a-z]+)*"));
    }

    @Test
    void catalogIsExactlyTheOneRegisteredInAdr0004() {
        // Mudar esta lista exige atualizar o ADR-0004 / api-contract.md: slug é contrato.
        assertThat(Arrays.stream(ProblemType.values()).map(ProblemType::slug)).containsExactly(
                "bad-request", "validation-error", "unauthorized", "forbidden", "not-found",
                "method-not-allowed", "unsupported-media-type", "internal-error", "invalid-token");
    }

    @ParameterizedTest
    @EnumSource(ProblemType.class)
    void uriIsAValidUrnWithTheSlug(ProblemType type) {
        URI uri = type.uri();

        assertThat(uri.getScheme()).isEqualTo("urn");
        assertThat(uri.toString()).isEqualTo("urn:nexa-finance:problem:" + type.slug());
    }

    @ParameterizedTest
    @CsvSource({
            "400, BAD_REQUEST", "401, UNAUTHORIZED", "403, FORBIDDEN", "404, NOT_FOUND",
            "405, METHOD_NOT_ALLOWED", "415, UNSUPPORTED_MEDIA_TYPE", "500, INTERNAL",
            // Sem slug próprio: cai na família, nunca num type ad hoc.
            "406, BAD_REQUEST", "413, BAD_REQUEST", "503, INTERNAL"})
    void forStatusMapsToTheCatalog(int status, ProblemType expected) {
        assertThat(ProblemType.forStatus(HttpStatusCode.valueOf(status))).isEqualTo(expected);
    }
}
