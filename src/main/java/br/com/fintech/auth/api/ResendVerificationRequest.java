package br.com.fintech.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Corpo do reenvio da confirmação. */
public record ResendVerificationRequest(
        @Schema(example = "maria@example.com") @NotBlank @Email @Size(max = 254) @Nullable String email) {

    public ResendVerificationRequest {
        email = email == null ? null : email.strip();
    }

    @Override
    public String toString() {
        return "ResendVerificationRequest[email=<oculto>]";
    }
}
