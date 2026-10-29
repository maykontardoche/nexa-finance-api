package br.com.fintech.auth.api;

import br.com.fintech.auth.application.RegisterCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Corpo do cadastro. Nome e e-mail perdem os espaços das pontas antes da validação; a senha
 * chega intacta (espaço também é caractere de senha).
 */
public record RegisterRequest(
        @Schema(example = "Maria Silva",
                description = "De 1 a 100 caracteres, sem contar espaços nas pontas; sem quebras de linha")
        // Controle, surrogate solto e separador de linha saem aqui, antes de o cadastro se dividir entre conta nova
        // e existente: um valor que só o insert recusasse responderia diferente nos dois casos.
        @NotBlank @Size(max = 100) @Pattern(regexp = SINGLE_LINE_TEXT, message = SINGLE_LINE_MESSAGE)
        @Nullable String fullName,

        @Schema(example = "maria@example.com", description = "Sem diferença entre maiúsculas e minúsculas")
        @NotBlank @Email @Size(max = 254) @Nullable String email,

        @Schema(example = "uma frase longa que só eu sei",
                description = "De 15 a 128 caracteres; qualquer caractere vale. Senhas comuns e senhas que "
                        + "contêm o e-mail são recusadas")
        // Teto bruto antes da normalização e do hash; a política (15 a 128) vem depois.
        @NotNull @Size(max = 1024) @Nullable String password) {

    static final String SINGLE_LINE_TEXT = "[^\\p{Cc}\\p{Cs}\\p{Zl}\\p{Zp}]*";
    static final String SINGLE_LINE_MESSAGE = "não pode ter quebras de linha nem caracteres de controle";

    public RegisterRequest {
        fullName = fullName == null ? null : fullName.strip();
        email = email == null ? null : email.strip();
    }

    RegisterCommand toCommand() {
        return new RegisterCommand(fullName, email, password);
    }

    /** Sem a senha: o corpo pode acabar num log de validação. */
    @Override
    public String toString() {
        return "RegisterRequest[fullName=<oculto>, email=<oculto>, password=<oculto>]";
    }
}
