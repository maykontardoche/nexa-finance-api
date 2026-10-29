package br.com.fintech.auth.application;

/** Dados do cadastro, como chegaram (e-mail ainda não normalizado). */
public record RegisterCommand(String fullName, String email, String password) {

    /** Sem a senha nem o e-mail: o comando pode acabar num log de erro. */
    @Override
    public String toString() {
        return "RegisterCommand[fullName=<oculto>, email=<oculto>, password=<oculto>]";
    }
}
