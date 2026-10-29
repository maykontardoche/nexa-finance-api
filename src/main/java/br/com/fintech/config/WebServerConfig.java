package br.com.fintech.config;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O Tomcat recusa {@code %2F} e {@code %5C} no caminho por conta própria, com uma página HTML fora
 * do contrato de erro. Repassando as barras codificadas, quem recusa é o firewall do
 * Spring Security ({@code StrictHttpFirewall} não aceita nenhuma das duas), e a resposta sai em
 * {@code application/problem+json} como qualquer outro 400.
 */
@Configuration(proxyBeanMethods = false)
public class WebServerConfig {

    @Bean
    WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory> encodedSlashReachesTheFirewall() {
        String passThrough = EncodedSolidusHandling.PASS_THROUGH.getValue();
        return factory -> factory.addConnectorCustomizers(connector -> {
            connector.setEncodedSolidusHandling(passThrough);
            connector.setEncodedReverseSolidusHandling(passThrough);
        });
    }
}
