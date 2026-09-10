package com.devsos.infrastructure.repository;

import com.devsos.domain.session.SessionEntity;
import com.devsos.domain.session.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de {@link SessionEntity} (as "corridas").
 *
 * <p>Além do CRUD do {@link JpaRepository}, temos as consultas do domínio:</p>
 * <ul>
 *   <li><b>{@code findByPostIdAndStatusIn}</b> — usado pelo service no aceite:
 *       existe corrida MATCHED/ACTIVE neste post? (espelha o índice único).</li>
 *   <li><b>{@code findMinhasSessoes}</b> — JPQL que trás as corridas onde você
 *       é o helper OU o autor do post. Como o autor vive em {@code post.author},
 *       precisamos do {@code JOIN s.post} (caminho de navegação, não SQL puro).</li>
 * </ul>
 */
@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    Optional<SessionEntity> findByPostIdAndStatusIn(UUID postId, Collection<SessionStatus> statuses);

    /**
     * Sala de chat única por corrida — usado pelo chat para autorizar
     * participantes. O {@code @EntityGraph} carrega {@code post.author} e
     * {@code helper} na MESMA query porque este finder roda fora de transação
     * (interceptor de canal do WebSocket, sem OpenSessionInView) — sem o fetch
     * eager, acessar {@code session.getPost()} dispararia LazyInitializationException.
     */
    @EntityGraph(attributePaths = {"post.author", "helper"})
    Optional<SessionEntity> findByChatRoomId(UUID chatRoomId);

    Page<SessionEntity> findByHelperId(UUID helperId, Pageable pageable);

    @Query("SELECT s FROM SessionEntity s JOIN s.post p WHERE s.helper.id = :userId OR p.author.id = :userId ORDER BY s.createdAt DESC")
    Page<SessionEntity> findMinhasSessoes(@Param("userId") UUID userId, Pageable pageable);
}