package br.com.fintech.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestOriginResolverTest {

    private static final String CHROME_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final String FIREFOX_LINUX =
            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0";
    private static final String SAFARI_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";

    private final RequestOriginResolver resolver = new RequestOriginResolver();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "203.0.113.57, 203.0.113.0",
        "10.0.0.1, 10.0.0.0",
        "255.255.255.255, 255.255.255.0",
        "127.0.0.1, 127.0.0.0",
        "' 198.51.100.9 ', 198.51.100.0",
        // Forma curta a.b.c, aceita pelo Java: o último número tem 16 bits (203.0.0.113).
        "203.0.113, 203.0.0.0",
        // IPv4 mapeado em IPv6 vira IPv4.
        "::ffff:203.0.113.57, 203.0.113.0",
        "2001:db8:abcd:12:34:56:78:9a, 2001:db8:abcd::",
        "2001:0DB8:ABCD:0012::1, 2001:db8:abcd::",
        "2001:db8::1, 2001:db8::",
        "::1, ::",
        "fe80::1%1, fe80::",
        // Zero no meio do prefixo não é comprimido: o "::" fica com a sequência final, mais longa.
        "2001:0:abcd:1::1, 2001:0:abcd::",
    })
    void truncatesIpv4To24AndIpv6To48Bits(String address, String expected) {
        assertThat(RequestOriginResolver.truncateIp(address)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "localhost", "exemplo.com", "999.1.1.1", "2001:db8::zz", "não é ip"})
    void invalidAddressesBecomeNullWithoutAnyDnsLookup(String address) {
        assertThat(RequestOriginResolver.truncateIp(address)).isNull();
    }

    @Test
    void knownUserAgentsBecomeBrowserAndSystem() {
        assertThat(resolver.describeDevice(CHROME_WINDOWS)).isEqualTo("Chrome 131 / Windows 10");
        assertThat(resolver.describeDevice(FIREFOX_LINUX)).isEqualTo("Firefox 140 / Ubuntu");
        assertThat(resolver.describeDevice(SAFARI_IPHONE)).isEqualTo("Mobile Safari 17 / iOS 17");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "xyz", "????"})
    void absentOrUnrecognizedUserAgentBecomesNull(String userAgent) {
        assertThat(resolver.describeDevice(userAgent)).isNull();
    }

    @Test
    void hugeUserAgentIsCappedAndTheResultFitsTheColumn() {
        String huge = CHROME_WINDOWS + " " + "A".repeat(100_000);

        assertThat(resolver.describeDevice(huge)).isEqualTo("Chrome 131 / Windows 10");
    }

    @Test
    void unknownBotNameIsCopiedFromTheUserAgentText() {
        // O uap-core monta o nome a partir do próprio texto: por isso a limpeza testada abaixo.
        String crafted = "Mozilla/5.0 (compatible; " + "X".repeat(200) + "bot/1.0)";

        assertThat(resolver.describeDevice(crafted)).isEqualTo("X".repeat(50) + "bot 1");
    }

    @Test
    void cleanRemovesControlCharactersAndCapsAtTheColumnSize() {
        assertThat(RequestOriginResolver.clean("Fire\u0007fox\u0000 1\n / Linux\t")).isEqualTo("Firefox 1 / Linux");
        assertThat(RequestOriginResolver.clean("a".repeat(500))).hasSize(RequestOriginResolver.MAX_DEVICE);
        // Fora do BMP: corta em code points (a coluna conta caracteres), sem deixar par partido.
        String clefs = RequestOriginResolver.clean("𝄞".repeat(200));
        assertThat(clefs.codePointCount(0, clefs.length())).isEqualTo(RequestOriginResolver.MAX_DEVICE);
        assertThat(Character.isHighSurrogate(clefs.charAt(clefs.length() - 1))).isFalse();
    }

    @Test
    void resolvesFromTheConnectionAddressIgnoringForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.57");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        request.addHeader("User-Agent", FIREFOX_LINUX);

        assertThat(resolver.resolve(request)).isEqualTo(new RequestOrigin("203.0.113.0", "Firefox 140 / Ubuntu"));
    }
}
