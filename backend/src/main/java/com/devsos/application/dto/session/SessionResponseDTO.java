package com.devsos.application.dto.session;

import com.devsos.domain.session.SessionEntity;
import com.devsos.domain.session.SessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Visão completa de uma "corrida" devolvida ao cliente.
 * Já traz os dois lados (autor e helper) e o título do post, para o app
 * renderizar a chamada de ajuda sem precisar de outras chamadas.
 */
public record SessionResponseDTO(
    UUID id,
    UUID postId,
    String postTitulo,
    BigDecimal recompensaValor,
    SessionStatus status,
    UUID chatRoomId,
    UUID authorId,
    String authorNome,
    UUID helperId,
    String helperNome,
    Instant completedAt,
    Instant createdAt
) {

    public static SessionResponseDTO from(SessionEntity s) {
        return new SessionResponseDTO(
            s.getId(),
            s.getPost().getId(),
            s.getPost().getTitulo(),
            s.getPost().getRecompensaValor(),
            s.getStatus(),
            s.getChatRoomId(),
            s.getPost().getAuthor().getId(),
            s.getPost().getAuthor().getNome(),
            s.getHelper().getId(),
            s.getHelper().getNome(),
            s.getCompletedAt(),
            s.getCreatedAt()
        );
    }
}