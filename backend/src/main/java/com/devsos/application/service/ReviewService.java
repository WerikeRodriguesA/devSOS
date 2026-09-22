package com.devsos.application.service;

import com.devsos.application.dto.review.ReviewCreateRequestDTO;
import com.devsos.application.dto.review.ReviewResponseDTO;
import com.devsos.application.dto.review.ReviewUpdateRequestDTO;
import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.application.exception.ResourceNotFoundException;
import com.devsos.domain.review.ReviewEntity;
import com.devsos.domain.session.SessionEntity;
import com.devsos.domain.session.SessionStatus;
import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.repository.ReviewRepository;
import com.devsos.infrastructure.repository.SessionRepository;
import com.devsos.infrastructure.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serviço de avaliações mútuas (o "aha!" do DevSOS).
 *
 * <h2>Como funciona a avaliação</h2>
 * <ol>
 *   <li>Só dá para avaliar corridas {@code COMPLETED} — critério justo;</li>
 *   <li>O avaliador é o usuário logado; o avaliado é SEMPRE o outro lado da
 *       corrida (se você é o autor, avalia o helper; se é o helper, avalia o autor)
 *       — o cliente não escolhe quem avaliar (impede avaliação de estranhos);</li>
 *   <li>1 review por pessoa por corrida (regra também no banco: UNIQUE);</li>
 *   <li>A média do avaliado é recalculada pelo TRIGGER do banco
 *       ({@code fn_recalc_user_rating}) — o Java relê o usuário e devolve a
 *       média nova já na resposta.</li>
 * </ol>
 */
@Service
public class ReviewService {

    private static final String RECURSO_CORRIDA = "Corrida";
    private static final String RECURSO_USUARIO = "Usuário";
    private static final String RECURSO_AVALIACAO = "Avaliação";

    private final ReviewRepository reviewRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         SessionRepository sessionRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ReviewResponseDTO avaliar(UUID reviewerId, ReviewCreateRequestDTO request) {
        SessionEntity sessao = sessionRepository.findById(request.sessionId())
            .orElseThrow(() -> ResourceNotFoundException.of(RECURSO_CORRIDA));

        if (sessao.getStatus() != SessionStatus.COMPLETED) {
            throw new RegraDeNegocioException("Só é possível avaliar depois que a corrida é concluída.");
        }

        UserEntity reviewer = userRepository.findById(reviewerId)
            .orElseThrow(() -> ResourceNotFoundException.of(RECURSO_USUARIO));

        boolean eAutor = sessao.getPost().getAuthor().getId().equals(reviewerId);
        boolean eHelper = sessao.getHelper().getId().equals(reviewerId);
        if (!eAutor && !eHelper) {
            throw new RegraDeNegocioException("Somente quem participou da corrida pode avaliar.");
        }
        if (reviewRepository.existsBySessionIdAndReviewerId(sessao.getId(), reviewerId)) {
            throw new RegraDeNegocioException("Você já avaliou esta corrida.");
        }

        UserEntity reviewed = eAutor ? sessao.getHelper() : sessao.getPost().getAuthor();
        String comentario = request.comentario() == null ? "" : request.comentario().trim();

        reviewRepository.saveAndFlush(
            ReviewEntity.criar(sessao, reviewer, reviewed, request.nota(), comentario)
        );

        // O saveAndFlush acima dispara o SELECT de retorno dos campos gerados
        // (created_at via insertable=false). Reler para pegar id + média nova.
        ReviewEntity salva = reviewRepository
            .findBySessionIdAndReviewerId(sessao.getId(), reviewerId)
            .orElseThrow();

        UserEntity reviewedAtualizado = userRepository.findById(reviewed.getId())
            .orElseThrow(() -> ResourceNotFoundException.of(RECURSO_USUARIO));

        return ReviewResponseDTO.from(salva, reviewedAtualizado.getMediaAvaliacoes());
    }

    /** Avaliações que eu recebi (para o app mostrar "minha reputação"). */
    @Transactional(readOnly = true)
    public PagedModel<ReviewResponseDTO> listarRecebidas(UUID userId, Pageable pageable) {
        Page<ReviewResponseDTO> page = reviewRepository
            .findByReviewedId(userId, pageable)
            .map(r -> ReviewResponseDTO.from(r, r.getReviewed().getMediaAvaliacoes()));
        return new PagedModel<>(page);
    }

    /**
     * PATCH /api/reviews/{id} — edita a MINHA avaliação (issue #20).
     * <p>Só o {@code reviewer} (dono, vindo do token) pode mudar nota/comentário
     * da própria review; avaliação de terceiros → regra de negócio (400). A
     * média do avaliado reajusta via trigger do banco (agora com AFTER UPDATE) —
     * o método relê o usuário para devolver a média nova na resposta.</p>
     */
    @Transactional
    public ReviewResponseDTO atualizar(UUID reviewerId, UUID reviewId, ReviewUpdateRequestDTO request) {
        ReviewEntity review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> ResourceNotFoundException.of(RECURSO_AVALIACAO));

        if (!review.getReviewer().getId().equals(reviewerId)) {
            throw new RegraDeNegocioException("Você só pode editar a sua própria avaliação.");
        }

        if (request.nota() != null) {
            review.setNota(request.nota());
        }
        if (request.comentario() != null && !request.comentario().trim().isEmpty()) {
            review.setComentario(request.comentario().trim());
        }

        reviewRepository.saveAndFlush(review);

        UserEntity reviewedAtualizado = userRepository.findById(review.getReviewed().getId())
            .orElseThrow(() -> ResourceNotFoundException.of(RECURSO_USUARIO));

        return ReviewResponseDTO.from(review, reviewedAtualizado.getMediaAvaliacoes());
    }

    /**
     * DELETE /api/reviews/{id} — apaga a MINHA avaliação (issue #20).
     * <p>Mesma proteção de dono do PATCH. O trigger (agora com AFTER DELETE)
     * recalcula a média do avaliado sem a review removida.</p>
     */
    @Transactional
    public void excluir(UUID reviewerId, UUID reviewId) {
        ReviewEntity review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> ResourceNotFoundException.of(RECURSO_AVALIACAO));

        if (!review.getReviewer().getId().equals(reviewerId)) {
            throw new RegraDeNegocioException("Você só pode apagar a sua própria avaliação.");
        }

        reviewRepository.delete(review);
        reviewRepository.flush();
    }
}