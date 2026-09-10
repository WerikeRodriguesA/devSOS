package com.devsos.infrastructure.repository;

import com.devsos.domain.chat.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório de {@link ChatMessageEntity} — mensagens da sala de chat.
 *
 * <p>A consulta de histórico é filtrada por {@code session} (a corrida), porque
 * é a corrida que carrega os participantes (autor + helper). Quem não participa
 * nem consegue listar.</p>
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}