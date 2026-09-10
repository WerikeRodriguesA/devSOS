package com.devsos.infrastructure.repository;

import com.devsos.domain.post.PostEntity;
import com.devsos.domain.post.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Camada de infraestrutura: repositório de {@link PostEntity}.
 */
@Repository
public interface PostRepository extends JpaRepository<PostEntity, UUID> {

    /**
     * Consulta CUSTOMIZADA para o feed principal:
     * lista os posts com status {@link PostStatus#OPEN} (os "disponíveis para
     * socorro") paginados.
     * <p>
     * {@code @EntityGraph(attributePaths = "author")}: evita o problema
     * <b>N+1</b> — em vez de 1 SELECT por post para buscar o autor, o Hibernate
     * faz um único SELECT com {@code LEFT JOIN FETCH} para trazer autor+posts
     * juntos. (N+1 = "1 consulta da lista + N consultas dos detalhes").
     * <p>
     * A ordenação por mais recentes fica a cargo do {@code Pageable} montado
     * no Service ({@code Sort.by("createdAt").descending()}). O índice
     * {@code idx_posts_feed (status, created_at DESC)} da nossa DDL casou
     * perfeitamente com esta query.
     */
    @EntityGraph(attributePaths = "author")
    Page<PostEntity> findByStatus(PostStatus status, Pageable pageable);

    /**
     * Direcionado ao filtro "meus posts": consulta por autor e status,
     * também com {@code EntityGraph} para evitar N+1.
     */
    @EntityGraph(attributePaths = "author")
    Page<PostEntity> findByAuthorIdAndStatus(UUID authorId, PostStatus status, Pageable pageable);
}