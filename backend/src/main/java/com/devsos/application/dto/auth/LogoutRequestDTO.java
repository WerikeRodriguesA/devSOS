package com.devsos.application.dto.auth;

/**
 * Corpo de {@code POST /api/auth/logout}.
 *
 * <p><b>Comportamento:</b></p>
 * <ul>
 *   <li>com {@code refreshToken} → revoga só este dispositivo (token específico);</li>
 *   <li>sem {@code refreshToken} → <b>logout forçado</b>: revoga TODOS os
 *       refresh tokens do usuário autenticado.</li>
 * </ul>
 */
public record LogoutRequestDTO(
    String refreshToken
) {}