package com.devsos.application.dto.review;

import com.devsos.domain.review.ReviewEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Avaliação devolvida ao cliente.
 * Inclui a {@code mediaAvaliacoesDoAvaliado} (recalculada pelo trigger do banco)
 * para o front já mostrar a nota atualizada do dev avaliado.
 */
public record ReviewResponseDTO(
    UUID id,
    UUID sessionId,
    UUID reviewerId,
    String reviewerNome,
    UUID reviewedId,
    String reviewedNome,
    Short nota,
    String comentario,
    BigDecimal mediaAvaliacoesDoAvaliado,
    Instant createdAt
) {

    public static ReviewResponseDTO from(ReviewEntity r, BigDecimal mediaAvaliacoesDoAvaliado) {
        return new ReviewResponseDTO(
            r.getId(),
            r.getSession().getId(),
            r.getReviewer().getId(),
            r.getReviewer().getNome(),
            r.getReviewed().getId(),
            r.getReviewed().getNome(),
            r.getNota(),
            r.getComentario(),
            mediaAvaliacoesDoAvaliado,
            r.getCreatedAt()
        );
    }
}