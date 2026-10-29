package br.com.fintech.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import ua_parser.Client;
import ua_parser.Parser;

/**
 * Extrai a {@link RequestOrigin} da requisição.
 *
 * <p>O IP é o da conexão ({@code getRemoteAddr}). O {@code X-Forwarded-For} é ignorado de
 * propósito: não há proxy na frente da API, e o cliente poderia forjar a própria origem.
 */
@Component
public class RequestOriginResolver {

    // Limite antes do parser, que roda centenas de regex sobre o texto inteiro.
    static final int MAX_USER_AGENT = 512;
    // Tamanho das colunas device das sessões e dos eventos.
    static final int MAX_DEVICE = 120;
    private static final String UNKNOWN = "Other";

    // Carrega e compila as regex do uap-core uma vez; parse() não guarda estado.
    private final Parser parser = new Parser();

    public RequestOrigin resolve(HttpServletRequest request) {
        return new RequestOrigin(truncateIp(request.getRemoteAddr()), describeDevice(request.getHeader(HttpHeaders.USER_AGENT)));
    }

    /**
     * {@code 203.0.113.0} para IPv4 (/24) e {@code 2001:db8:abcd::} para IPv6 (/48), na forma
     * canônica da RFC 5952 (minúsculas, sem zeros à esquerda, zeros finais comprimidos).
     */
    static @Nullable String truncateIp(@Nullable String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        InetAddress parsed;
        try {
            // ofLiteral nunca consulta DNS: só aceita o endereço numérico.
            parsed = InetAddress.ofLiteral(address.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
        byte[] bytes = parsed.getAddress();
        if (parsed instanceof Inet4Address) {
            return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + "." + (bytes[2] & 0xff) + ".0";
        }
        // Os 80 bits finais são zero: essa sequência é sempre a mais longa e vira o "::".
        List<String> prefix = new ArrayList<>(List.of(
                Integer.toHexString(readHextet(bytes, 0)),
                Integer.toHexString(readHextet(bytes, 2)),
                Integer.toHexString(readHextet(bytes, 4))));
        while (!prefix.isEmpty() && prefix.getLast().equals("0")) {
            prefix.removeLast();
        }
        return String.join(":", prefix) + "::";
    }

    /** {@code Firefox 140 / Windows 10}; nulo se nem navegador nem sistema forem reconhecidos. */
    @Nullable String describeDevice(@Nullable String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String capped = userAgent.length() > MAX_USER_AGENT ? userAgent.substring(0, MAX_USER_AGENT) : userAgent;
        Client client = parser.parse(capped);
        List<String> parts = new ArrayList<>(2);
        addKnown(parts, client.userAgent.family, client.userAgent.major);
        addKnown(parts, client.os.family, client.os.major);
        if (parts.isEmpty()) {
            return null;
        }
        return clean(String.join(" / ", parts));
    }

    private static int readHextet(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static void addKnown(List<String> parts, @Nullable String family, @Nullable String major) {
        if (family == null || family.isBlank() || UNKNOWN.equals(family)) {
            return;
        }
        parts.add(major == null || major.isBlank() ? family : family + " " + major);
    }

    /**
     * Algumas regex do uap-core copiam trechos do user-agent para o nome: tira caracteres de
     * controle e corta no tamanho da coluna, sem partir um caractere fora do BMP.
     */
    static String clean(String value) {
        String printable = value.codePoints()
                .filter(cp -> !Character.isISOControl(cp))
                .limit(MAX_DEVICE)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        return printable.strip();
    }
}
