package br.com.fintech.shared.web;

import org.jspecify.annotations.Nullable;

/**
 * Origem aproximada de uma requisição, para sessões e atividade de segurança. Nunca guarda o IP
 * completo nem o user-agent bruto: só o que basta para o usuário reconhecer um acesso.
 *
 * @param ip IP truncado (/24 no IPv4, /48 no IPv6); nulo se o endereço não for um literal válido
 * @param device navegador e sistema aproximados (ex.: {@code Firefox 140 / Windows 10}); nulo
 *     se o user-agent estiver ausente ou não for reconhecido
 */
public record RequestOrigin(@Nullable String ip, @Nullable String device) {
}
