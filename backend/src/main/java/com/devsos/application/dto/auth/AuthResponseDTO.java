package com.devsos.application.dto.auth;

import com.devsos.application.dto.user.UserProfileResponseDTO;

/**
 * Resposta do registro/login/refresh: perfil do usuário + access JWT +
 * refresh token (opaco, com TTL próprio).
 *
 * <p>{@code token} deve ser enviado de volta em toda requisição protegida no
 * cabeçalho {@code Authorization: Bearer <token>}.</p>
 *
 * <p>{@code refreshToken} serve para renovar o access sem pedir a senha
 * novamente ({@code POST /api/auth/refresh}) e para deslogar
 * ({@code POST /api/auth/logout}). Ele é rotacionado a cada uso.</p>
 */
public record AuthResponseDTO(
    String token,
    long expiraEmSegundos,
    UserProfileResponseDTO usuario,
    String refreshToken,
    long refreshExpiraEmSegundos
) {}