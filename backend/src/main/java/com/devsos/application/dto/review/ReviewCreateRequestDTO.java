package com.devsos.application.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Avaliação de uma corrida concluída ({@code POST /api/reviews}).
 * O {@code sessionId} identifica a corrida; o avaliador é o usuário logado e o
 * avaliado é SEMPRE o outro lado (autor ↔ helper) — o cliente não escolhe quem
 * avaliar.
 */
public record ReviewCreateRequestDTO(

    @NotNull(message = "Informe o id da corrida (sessão)")
    UUID sessionId,

    @NotNull(message = "Dê uma nota de 1 a 5")
    @Min(value = 1, message = "A nota mínima é 1")
    @Max(value = 5, message = "A nota máxima é 5")
    Short nota,

    @Size(max = 1000, message = "O comentário deve ter no máximo 1000 caracteres")
    String comentario
) {}