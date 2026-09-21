package com.devsos.infrastructure.repository;

import com.devsos.domain.post.PostEntity;
import com.devsos.domain.post.PostStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
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

    /**
     * Busca o post com {@code PESSIMISTIC_WRITE} (SELECT ... FOR UPDATE) —
     * usado APENAS no caminho do aceite do socorro (issue #13).
     * <p>
     * Serializa os "aceites" concorrentes: a 2ª transação que tenta aceitar o
     * MESMO post espera o lock da linha até a 1ª terminar e, ao reler, vê o
     * post com status {@code IN_PROGRESS} — aí o Service entrega 400 amigável
     * ("já em atendimento") em vez de só o 409 do índice único. Usamos o
     * {@code EntityGraph} para o auto também vir junto (checagem de self-help).</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM PostEntity p WHERE p.id = :id")
    Optional<PostEntity> findByIdComLock(@Param("id") UUID id);
}