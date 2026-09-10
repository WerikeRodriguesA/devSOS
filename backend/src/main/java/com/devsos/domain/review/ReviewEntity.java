package com.devsos.domain.review;

import com.devsos.domain.session.SessionEntity;
import com.devsos.domain.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Entidade JPA da tabela {@code reviews} — a avaliação mútua pós-corrida.
 *
 * <h2>Regras de negócio (aqui e no banco)</h2>
 * <ul>
 *   <li><b>1 review por pessoa por corrida</b> — {@code UNIQUE (session_id, reviewer_id)};</li>
 *   <li><b>Não dá para autoavaliar</b> — {@code CHECK (reviewer_id <> reviewed_id)};</li>
 *   <li><b>Média automática</b> — o TRIGGER {@code trg_reviews_rating} do banco
 *       recalcula {@code users.media_avaliacoes} a cada review inserida. O Java
 *       apenas relê o usuário avaliado para devolver a média nova na resposta.</li>
 * </ul>
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionEntity session;

    /** Quem está avaliando (o autor OU o helper da corrida). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private UserEntity reviewer;

    /** Quem está sendo avaliado (sempre o OUTRO lado da corrida). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_id", nullable = false)
    private UserEntity reviewed;

    /** Nota de 1 a 5 (SMALLINT no banco, {@code Short} no Java). */
    @Column(name = "nota", nullable = false)
    private Short nota;

    @Column(name = "comentario", nullable = false)
    private String comentario = "";

    @CreationTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public static ReviewEntity criar(SessionEntity session, UserEntity reviewer,
                                     UserEntity reviewed, Short nota, String comentario) {
        ReviewEntity r = new ReviewEntity();
        r.session = session;
        r.reviewer = reviewer;
        r.reviewed = reviewed;
        r.nota = nota;
        r.comentario = comentario;
        return r;
    }
}