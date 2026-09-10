package com.devsos.application.dto.user;

import com.devsos.domain.user.UserEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Perfil público de um usuário. Devolvido pelo {@code GET /api/users/{id}}.
 * <p>
 * Note que o {@code email} é o ÚNICO dado sensível visible aqui — no mundo
 * real, outras pessoas não deveriam ver o e-mail (só o dono do perfil).
 * Mantemos apenas para simplificar o MVP; em produção a visão do perfil seria
 * separada (Perfil Público x Privado).
 */
public record UserProfileResponseDTO(
    UUID id,
    String nome,
    String email,
    String bio,
    String githubUsername,
    String avatarUrl,
    Integer saldoPontos,
    BigDecimal mediaAvaliacoes,
    List<String> tecnologiasDominadas,
    Instant createdAt
) {

    public static UserProfileResponseDTO from(UserEntity e) {
        return new UserProfileResponseDTO(
            e.getId(),
            e.getNome(),
            e.getEmail(),
            e.getBio(),
            e.getGithubUsername(),
            e.getAvatarUrl(),
            e.getSaldoPontos(),
            e.getMediaAvaliacoes(),
            e.getTecnologiasDominadas() == null ? List.of() : e.getTecnologiasDominadas(),
            e.getCreatedAt()
        );
    }
}