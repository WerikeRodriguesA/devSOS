package com.devsos.application.dto.auth;

import com.devsos.application.dto.user.UserProfileResponseDTO;

/**
 * Resposta do registro/login: perfil do usuário + token JWT + expiração.
 *
 * <p>{@code token} deve ser enviado de volta em toda requisição protegida no
 * cabeçalho {@code Authorization: Bearer <token>}.</p>
 */
public record AuthResponseDTO(
    String token,
    long expiraEmSegundos,
    UserProfileResponseDTO usuario
) {}