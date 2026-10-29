package br.com.fintech.auth.application;

import br.com.fintech.auth.domain.EmailTokenPurpose;
import br.com.fintech.shared.mail.MailLinks;
import br.com.fintech.shared.mail.MailMessage;
import org.springframework.stereotype.Component;

/** Textos dos e-mails de identidade: pt-BR, texto puro e com o link escrito por extenso. */
@Component
public class AuthMails {

    static final String VERIFICATION_PATH = "/api/v1/auth/email-verification";
    static final String RESEND_PATH = "/api/v1/auth/email-verification/resend";

    private final MailLinks links;

    public AuthMails(MailLinks links) {
        this.links = links;
    }

    /**
     * Link de confirmação. Sem o nome digitado no cadastro: este e-mail vai para um endereço que
     * qualquer pessoa pode informar, e o nome seria texto livre dela dentro de uma mensagem nossa.
     */
    public MailMessage emailVerification(String to, String token) {
        String link = links.buildLink(VERIFICATION_PATH, "token", token);
        long hours = EmailTokenPurpose.EMAIL_VERIFICATION.validity().toHours();
        return new MailMessage("email-verification", to, "Confirme seu e-mail na Nexa Finance", """
                Olá!

                Para ativar sua conta na Nexa Finance, confirme seu e-mail abrindo o link abaixo:

                %s

                O link vale por %d horas e só pode ser usado uma vez. Se ele expirar, peça um novo \
                pela API (POST %s, com o seu e-mail).

                Se você não criou esta conta, ignore este e-mail. Nada será feito sem a confirmação.
                """.formatted(link, hours, RESEND_PATH));
    }

    /** Aviso ao dono quando alguém tenta cadastrar o e-mail dele de novo. */
    public MailMessage registrationAttempt(String to, String fullName) {
        return new MailMessage("registration-attempt", to, "Tentativa de cadastro com o seu e-mail", """
                Olá, %s!

                Alguém tentou criar uma conta na Nexa Finance com este e-mail. Como ele já tem uma \
                conta, nada foi criado e nada mudou na sua conta.

                Se foi você, entre com a sua senha. Se ainda não confirmou o e-mail, peça um novo \
                link de confirmação pela API (POST %s).

                Se não foi você, não é preciso fazer nada.
                """.formatted(fullName, RESEND_PATH));
    }
}
