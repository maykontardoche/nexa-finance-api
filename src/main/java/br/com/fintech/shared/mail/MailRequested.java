package br.com.fintech.shared.mail;

/**
 * Evento de envio. Package-private de propósito: só o {@link MailNotifier} o cria, depois de
 * conferir que há transação; ninguém agenda envio fora dela nem dispara o listener direto.
 */
record MailRequested(MailMessage message) {
}
