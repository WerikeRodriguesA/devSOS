package com.devsos.domain.chat;

import com.devsos.domain.session.SessionEntity;
import com.devsos.domain.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade JPA da tabela {@code chat_messages} — uma mensagem da sala.
 *
 * <p>A mensagem é vinculada a DOIS identificadores (ver migração v4):</p>
 * <ul>
 *   <li><b>{@code session}</b> — a corrida. É por ela que validamos se quem
 *       lê o histórico é participante (autor ou helper);</li>
 *   <li><b>{@code chatRoomId}</b> — a sala em si (mesma coluna de
 *       {@code sessions.chat_room_id}). O WebSocket usa a sala para rotear a
 *       mensagem ao tópico {@code /topic/chat/{chatRoomId}}.</li>
 * </ul>
 *
 * <p><b>Mensagens não persistidas:</b> os tipos {@code JOIN}/{@code LEAVE} são
 * eventos do sistema (avisos de entrada/saída) e vivem só no tempo real — não
 * poluem o histórico.</p>
 */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionEntity session;

    /** Coluna denormalizada de conveniência (espelha {@code sessions.chat_room_id}). */
    @Column(name = "chat_room_id", nullable = false, updatable = false)
    private UUID chatRoomId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private UserEntity sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private ChatMessageType type;

    @Column(name = "conteudo", nullable = false)
    private String content;

    /** Delegado ao banco ({@code DEFAULT now()}) — mesmo padrão das outras entidades. */
    @CreationTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public static ChatMessageEntity registrar(SessionEntity session, UserEntity sender,
                                              ChatMessageType type, String content) {
        ChatMessageEntity m = new ChatMessageEntity();
        m.session = session;
        m.chatRoomId = session.getChatRoomId();
        m.sender = sender;
        m.type = type;
        m.content = content == null ? "" : content;
        return m;
    }
}