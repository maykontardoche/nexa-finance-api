package br.com.fintech.auth.api;

import br.com.fintech.auth.application.RegistrationService;
import br.com.fintech.shared.web.RequestOriginResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cadastro e confirmação de e-mail. Rotas públicas (ver {@code SecurityConfig.PUBLIC_ROUTES}). */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação", description = "Cadastro e confirmação de e-mail")
public class AuthController {

    private final RegistrationService registration;
    private final RequestOriginResolver origins;

    public AuthController(RegistrationService registration, RequestOriginResolver origins) {
        this.registration = registration;
        this.origins = origins;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Cadastrar uma conta",
            description = "A resposta é sempre 202, exista ou não uma conta com o e-mail. O link de "
                    + "confirmação (ou, se a conta já existe, um aviso) chega por e-mail.")
    @ApiResponse(responseCode = "202", description = "Pedido aceito; confira a caixa de e-mail")
    @ApiResponse(responseCode = "400", description = "Campo inválido ou senha recusada (`validation-error`)")
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        registration.register(request.toCommand(), origins.resolve(http));
    }

    @GetMapping("/email-verification")
    @Operation(summary = "Confirmar o e-mail",
            description = "Endereço do link enviado por e-mail. Vale uma vez, por 24 horas.")
    @ApiResponse(responseCode = "200", description = "E-mail confirmado")
    @ApiResponse(responseCode = "400", description = "Link inválido, expirado ou já usado (`invalid-token`)")
    public MessageResponse verifyEmail(
            @Parameter(description = "Token do link recebido por e-mail") @RequestParam(required = false)
                    @Nullable String token,
            HttpServletRequest http) {
        registration.verifyEmail(token, origins.resolve(http));
        return new MessageResponse("E-mail confirmado. Você já pode entrar na sua conta.");
    }

    @PostMapping("/email-verification/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Reenviar a confirmação de e-mail",
            description = "A resposta é sempre 202. Se houver conta não confirmada com o e-mail, um link "
                    + "novo é enviado e os anteriores deixam de valer.")
    @ApiResponse(responseCode = "202", description = "Pedido aceito")
    @ApiResponse(responseCode = "400", description = "E-mail ausente ou malformado (`validation-error`)")
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        registration.resendVerification(request.email());
    }
}
