package com.devsos.infrastructure.repository;

import com.devsos.domain.review.ReviewEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de {@link ReviewEntity} (avaliações pós-corrida).
 * A média de avaliações é mantida pelo TRIGGER do banco
 * ({@code fn_recalc_user_rating}); o Java consulta apenas existência (regra
 * "1 review por pessoa por corrida") e o histórico recebido.
 */
@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {

    boolean existsBySessionIdAndReviewerId(UUID sessionId, UUID reviewerId);

    Optional<ReviewEntity> findBySessionIdAndReviewerId(UUID sessionId, UUID reviewerId);

    Page<ReviewEntity> findByReviewedId(UUID reviewedId, Pageable pageable);
}