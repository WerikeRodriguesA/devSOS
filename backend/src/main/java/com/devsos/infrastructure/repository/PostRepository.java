package com.devsos.infrastructure.repository;

import com.devsos.domain.post.PostEntity;
import com.devsos.domain.post.PostStatus;
import com.devsos.domain.post.PostTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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
     * Hidrata a página de posts a partir dos IDs selecionados pela busca
     * nativa (evita N+1 no autor como as demais consultas do feed).
     */
    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM PostEntity p WHERE p.id IN :ids")
    List<PostEntity> carregarPostsPorId(@Param("ids") List<UUID> ids);

    /**
     * Feed com BUSCA e FILTROS (issue #16): query NATIVA que devolve apenas
     * os {@code id}s (o {@link Page} do resultado é do Spring Data). Os
     * filtros são opcionais — quem não é informado é ignorado pela condição
     * {@code :x IS NULL} (o PostgreSQL corta aquele predicado no plano).
     * <ul>
     *   <li>{@code q} — texto que deve CONTER em título OU descrição,
     *       case-insensitive ({@code ILIKE "%q%"}), acelerado pelo pg_trgm
     *       (índices GIN {@code idx_posts_titulo_trgm} e
     *       {@code idx_posts_descricao_trgm}, criados no V3);</li>
     *   <li>{@code tag} — contenção de array {@code @> ARRAY[:tag]::text[]},
     *       usando o índice GIN {@code idx_posts_tags} do V1;</li>
     *   <li>{@code tipo} — filtro por {@link PostTipo} (índice
     *       {@code idx_posts_status_tipo} do V1).</li>
     * </ul>
     * <p><b>Por que nativa e não JPQL?</b> O operador PostgreSQL {@code @>}
     * exige {@code text[] @> text[]}; o JPQL deixa o Hibernate escolher o tipo
     * do array do parâmetro e ele manda {@code character varying[]} (ou
     * {@code bytea[]}) — aí o postgres responde "operator does not exist".
     * Na query nativa o {@code ::text[]} resolve o tipo de forma explícita e o
     * índice GIN é usado de verdade.
     */
    @Query(value = """
        SELECT p.id FROM posts p
        WHERE p.status = :status
          AND (:q IS NULL OR LOWER(p.titulo)  LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(p.descricao) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:tag IS NULL OR p.tags @> ARRAY[:tag]::text[])
          AND (:tipo IS NULL OR p.tipo = :tipo)
        ORDER BY p.created_at DESC, p.id DESC
        """,
        countQuery = """
        SELECT count(*) FROM posts p
        WHERE p.status = :status
          AND (:q IS NULL OR LOWER(p.titulo)  LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(p.descricao) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:tag IS NULL OR p.tags @> ARRAY[:tag]::text[])
          AND (:tipo IS NULL OR p.tipo = :tipo)
        """,
        nativeQuery = true)
    Page<UUID> buscarIdsFeed(@Param("status") String status,
                             @Param("q") String q,
                             @Param("tag") String tag,
                             @Param("tipo") String tipo,
                             Pageable pageable);
}