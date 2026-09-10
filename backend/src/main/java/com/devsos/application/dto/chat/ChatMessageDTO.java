package com.devsos.application.dto.chat;

import com.devsos.domain.chat.ChatMessageEntity;
import com.devsos.domain.chat.ChatMessageType;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensagem do chat que trafega pelo WebSocket (e aparece no histórico).
 *
 * <h2>Quem preenche cada campo?</h2>
 * <ul>
 *   <li><b>{@code content}</b> e <b>{@code type}</b> — vêm do CLIENTE que envia
 *       (o texto digitado, e se é um trecho de código).</li>
 *   <li><b>{@code senderId}</b> — o servidor preenche com o usuário do token JWT
 *       (nunca confiamos no que o cliente digita aqui, senão alguém enviaria
 *       mensagem "como" outro dev).</li>
 *   <li><b>{@code timestamp}</b> — também do servidor: a hora em que o backend
 *       recebeu a mensagem (relógios de celulares mentem).</li>
 *   <li><b>{@code senderNome}</b> — conveniência de leitura: evita o front
 *       buscar o perfil de cada remetente para exibir o nome na bolha.</li>
 * </ul>
 *
 * <p><b>Por que um {@code record}?</b> Imutável, sem setter e com menos código —
 * exatamente o padrão dos demais DTOs do projeto (ver {@code PostResponseDTO}).</p>
 */
public record ChatMessageDTO(
    UUID senderId,
    String senderNome,
    String content,
    Instant timestamp,
    ChatMessageType type
) {

    public static ChatMessageDTO from(ChatMessageEntity msg) {
        return new ChatMessageDTO(
            msg.getSender().getId(),
            msg.getSender().getNome(),
            msg.getContent(),
            msg.getCreatedAt(),
            msg.getType()
        );
    }
}