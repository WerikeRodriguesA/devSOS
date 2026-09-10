package com.devsos.application.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Dados de LOGIN ({@code POST /api/auth/login}).
 * O cliente envia as credenciais e recebe o JWT (ver {@code AuthResponseDTO}).
 */
public record LoginRequestDTO(

    @NotBlank(message = "Informe seu e-mail")
    @Email(message = "E-mail inválido")
    String email,

    @NotBlank(message = "Informe sua senha")
    String senha
) {}