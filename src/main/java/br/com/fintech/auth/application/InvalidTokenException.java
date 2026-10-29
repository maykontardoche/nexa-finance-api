package br.com.fintech.auth.application;

/**
 * Token de e-mail inexistente, expirado ou já usado. O motivo exato não é dito ao cliente: os três
 * casos têm a mesma resposta, para o token não ser sondado.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("token de e-mail inválido, expirado ou já usado");
    }
}
