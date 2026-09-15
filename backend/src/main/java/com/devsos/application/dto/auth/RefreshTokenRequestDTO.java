package com.devsos.application.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de {@code POST /api/auth/refresh} — o refresh token opaco gerado no
 * login/cadastro (ou no refresh anterior, por rotação).
 */
public record RefreshTokenRequestDTO(
    @NotBlank(message = "Informe o refresh token")
    String refreshToken
) {}