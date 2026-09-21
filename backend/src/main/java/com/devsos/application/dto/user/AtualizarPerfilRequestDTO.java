package com.devsos.application.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request para editar o perfil completo ({@code PATCH /api/users/me}).
 *
 * <p><b>Semântica dos campos opcionais:</b> TODO campo é opcional —
 * {@code null} significa "não mexer" (edição parcial). Quem quiser LIMPAR um
 * campo manda uma string vazia ({@code ""}) no lugar.</p>
 *
 * <p><b>Formato de avatar:</b> aceita URL vazia (limpar o avatar) ou uma URL
 * absoluta começando em http/https — a regex abaixo substitui o {@code @URL}
 * do Hibernate justamente para permitir o vazio.</p>
 */
public record AtualizarPerfilRequestDTO(

    @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres")
    String nome,

    @Size(max = 500, message = "A bio deve ter no máximo 500 caracteres")
    String bio,

    @Size(max = 60, message = "GitHub username muito longo")
    @Pattern(
        regexp = "^$|^[A-Za-z0-9](?:[A-Za-z0-9-]{0,58}[A-Za-z0-9])?$",
        message = "GitHub username inválido (use letras, números e hífens; não pode terminar em hífen)"
    )
    String githubUsername,

    @Size(max = 255, message = "Avatar URL muito longa")
    @Pattern(
        regexp = "^$|^https?://\\S+$",
        message = "Avatar URL inválida (use http:// ou https:// seguido do endereço)"
    )
    String avatarUrl,

    @Size(max = 30, message = "O perfil suporta no máximo 30 tecnologias")
    List<
        @NotBlank(message = "Cada tecnologia precisa de um nome")
        @Size(max = 40, message = "Cada tecnologia deve ter no máximo 40 caracteres")
        String> tecnologiasDominadas
) {}