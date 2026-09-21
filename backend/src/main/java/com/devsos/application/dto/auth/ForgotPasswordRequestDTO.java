package com.devsos.application.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de {@code POST /api/auth/forgot-password}: só o e-mail.
 * A resposta é <b>sempre neutra</b> — exista ou não a conta.
 */
public record ForgotPasswordRequestDTO(

    @NotBlank(message = "Informe seu e-mail")
    @Email(message = "E-mail inválido")
    String email
) {
}
