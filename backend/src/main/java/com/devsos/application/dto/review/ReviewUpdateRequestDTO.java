package com.devsos.application.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Atualização da MINHA avaliação ({@code PATCH /api/reviews/{id}}).
 * Ambos os campos são opcionais (PATCH = altero só o que vier preenchido).
 * Com {@code null} o campo fica como estava; o comentário ignora {@code ""}
 * (string vazia) para não "zerar" o comentário sem querer.
 */
public record ReviewUpdateRequestDTO(

    @Min(value = 1, message = "A nota mínima é 1")
    @Max(value = 5, message = "A nota máxima é 5")
    Short nota,

    @Size(max = 1000, message = "O comentário deve ter no máximo 1000 caracteres")
    String comentario
) {}