package com.devsos.interfaces.controller;

import com.devsos.application.dto.review.ReviewCreateRequestDTO;
import com.devsos.application.dto.review.ReviewResponseDTO;
import com.devsos.application.service.ReviewService;
import com.devsos.infrastructure.security.IdUsuarioLogado;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avaliações mútuas pós-corrida.
 * <p>O avaliador vem do token; o avaliado é o OUTRO lado da corrida (não dá
 * para escolher quem avaliar). Exige JWT em todas as rotas.</p>
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** POST /api/reviews — avalia uma corrida COMPLETED (autor ↔ helper). */
    @PostMapping
    public ResponseEntity<ReviewResponseDTO> avaliar(
            @AuthenticationPrincipal IdUsuarioLogado logado,
            @Valid @RequestBody ReviewCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.avaliar(logado.id(), request));
    }

    /** GET /api/reviews — avaliações que eu RECEBI (minha reputação). */
    @GetMapping
    public ResponseEntity<PagedModel<ReviewResponseDTO>> listarRecebidas(
            @AuthenticationPrincipal IdUsuarioLogado logado,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reviewService.listarRecebidas(logado.id(), pageable));
    }
}