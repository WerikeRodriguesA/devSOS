package com.devsos.domain.session;

import com.devsos.domain.post.PostEntity;
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
 * Entidade JPA da tabela {@code sessions} — a "corrida" (chamada de ajuda).
 *
 * <h2>Quem é quem nessa corrida?</h2>
 * <ul>
 *   <li><b>{@code post}</b> — o problema publicado (tem o autor = quem pediu).</li>
 *   <li><b>{@code helper}</b> — o dev que aceitou o socorro (quem vai ajudar).</li>
 *   <li><b>{@code chatRoomId}</b> — identificador da sala de chat. O backend
 *       gera e entrega o ID; o chat em tempo real (WebSocket) é uma próxima
 *       iteração. Por isso a coluna guarda o UUID mas hoje só o expomos.</li>
 * </ul>
 *
 * <h2>Por que {@code chatRoomId} é setada no Java e não pelo DEFAULT do banco?</h2>
 * A DDL tem {@code DEFAULT gen_random_uuid()}, mas o Hibernate escreveria a
 * coluna no INSERT mesmo com valor {@code null} — "engolindo" o default. Para
 * não depender de {@code insertable=false} + releitura, geramos o UUID na
 * factory ({@link #aceitarSocorro}) e o valor já nasce conhecido no objeto.
 */
@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private PostEntity post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "helper_id", nullable = false)
    private UserEntity helper;

    /** Os valores são legíveis no banco e validados por um CHECK na DDL. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @Column(name = "chat_room_id", nullable = false, updatable = false)
    private UUID chatRoomId;

    /** Preenchido apenas quando a corrida chega em {@code COMPLETED}. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Mesmo padrão das demais entidades: timestamps delegados ao banco. */
    @CreationTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @CreationTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    /**
     * Factory (builder-ish): "aceitar socorro". Nasce como {@code MATCHED} com
     * uma sala de chat nova. A regra de "helper ≠ autor" e "1 corrida por post"
     * é validada no Service e revalidada pelo banco (trigger + índice único).
     */
    public static SessionEntity aceitarSocorro(PostEntity post, UserEntity helper) {
        SessionEntity s = new SessionEntity();
        s.post = post;
        s.helper = helper;
        s.status = SessionStatus.MATCHED;
        s.chatRoomId = UUID.randomUUID();
        return s;
    }
}