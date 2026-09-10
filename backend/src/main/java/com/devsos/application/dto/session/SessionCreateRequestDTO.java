package com.devsos.application.dto.session;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Aceitar socorro ({@code POST /api/sessions}).
 * Só o {@code postId} vem do cliente; o {@code helper} é o usuário logado
 * (vem do token JWT). O contrato não expõe {@code status}/{@code chatRoomId} —
 * o sistema define.
 */
public record SessionCreateRequestDTO(
    @NotNull(message = "Informe o id do post que você vai ajudar")
    UUID postId
) {}