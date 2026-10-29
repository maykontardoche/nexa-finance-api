package br.com.fintech.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Execução assíncrona (envio de e-mail). O decorator leva o contexto da observação para a thread
 * do executor: o log do envio sai com o mesmo {@code traceId} da requisição que o originou.
 * O Boot associa o único {@code TaskDecorator} ao executor que ele configura.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

    @Bean
    ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
