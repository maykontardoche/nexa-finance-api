package br.com.fintech.shared.mail;

/**
 * E-mail em texto puro, em pt-BR.
 *
 * @param kind identifica o tipo de e-mail no log (ex.: {@code email-verification}); é o único
 *     campo que aparece no log, porque destinatário e corpo têm dado pessoal e tokens
 * @param to destinatário
 * @param subject assunto
 * @param body corpo em texto, com os links também escritos por extenso
 */
public record MailMessage(String kind, String to, String subject, String body) {

    public MailMessage {
        if (kind.isBlank() || to.isBlank() || subject.isBlank() || body.isBlank()) {
            throw new IllegalArgumentException("e-mail incompleto: " + kind);
        }
    }

    /** Sem destinatário nem corpo: o record não vaza dado pessoal se for parar num log. */
    @Override
    public String toString() {
        return "MailMessage[kind=" + kind + "]";
    }
}
