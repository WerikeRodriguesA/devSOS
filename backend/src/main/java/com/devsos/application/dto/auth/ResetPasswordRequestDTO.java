package com.devsos.application.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/auth/reset-password}: o token recebido por e-mail
 * e a nova senha.
 */
public record ResetPasswordRequestDTO(

    @NotBlank(message = "Informe o token de recuperação")
    String token,

    @NotBlank(message = "Informe a nova senha")
    @Size(min = 8, max = 100, message = "A senha deve ter entre 8 e 100 caracteres")
    String novaSenha
) {
}
