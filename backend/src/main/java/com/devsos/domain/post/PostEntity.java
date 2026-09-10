package com.devsos.domain.post;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidade JPA que representa a tabela {@code posts} (o "mural de recados").
 *
 * <h2>Por que {@code @ManyToOne} e não uma coluna {@code UUID authorId}?</h2>
 * {@code authorId} como {@code UUID} cru já "funcionaria", mas perderíamos:
 * navegação orientada a objeto ({@code post.getAutor().getNome()}) e o
 * carregamento sob demanda (lazy) que o JPA traz.
 * <p>
 * Em contraste, expomos os DTOs com dados prontos (ex.: {@code autorNome})
 * para o front — justamente para nunca vazar a entidade.
 */
@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Relação com o autor. {@code FetchType.LAZY} é obrigatório em boas práticas:
     * se fosse EAGER, TODA consulta faria join mesmo sem precisar — desacelerando
     * o feed. A lista do feed usa {@code JOIN FETCH} para trazer o autor num
     * ALÉM de 1 consulta (evita N+1).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private UserEntity author;

    @Column(name = "titulo", length = 160, nullable = false)
    private String titulo;

    @Column(name = "descricao", nullable = false)
    private String descricao;

    @Column(name = "media_url")
    private String mediaUrl;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    /** {@code @Enumerated(EnumType.STRING)} salva "FREE"/"PAID" no banco. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private PostTipo tipo;

    @Column(name = "recompensa_valor", precision = 10, scale = 2, nullable = false)
    private BigDecimal recompensaValor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PostStatus status;

    @CreationTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    /**
     * As duas colunas de tempo são DELEGADAS ao banco:
     * <ul>
     *   <li>{@code insertable=false}: o Hibernate não envia nada no INSERT —
     *       o {@code DEFAULT now()} e o TRIGGER da nossa DDL cuidam do valor;</li>
     *   <li>{@code updatable=false}: os UPDATEs seguem valendo o trigger
     *       {@code trg_posts_touch};</li>
     *   <li>o Hibernate RELÊ a linha após o flush ({@code GenerationTime.ALWAYS}),
     *       então a resposta da API sempre traz os timestamps preenchidos.</li>
     * </ul>
     */
    @CreationTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    /**
     * Método com encadeamento (builder-like) para montar um post partindo do
     * autor. Mantém a entidade "anêmica" somente na persitência e empurra a
     * regra de negócio (ex.: validar recompensa positiva) para o Service.
     */
    public static PostEntity criar(UserEntity author,
                                    String titulo,
                                    String descricao,
                                    String mediaUrl,
                                    List<String> tags,
                                    PostTipo tipo,
                                    BigDecimal recompensaValor) {
        PostEntity p = new PostEntity();
        p.author = author;
        p.titulo = titulo;
        p.descricao = descricao;
        p.mediaUrl = mediaUrl;
        p.tags = tags;
        p.tipo = tipo;
        p.recompensaValor = recompensaValor;
        p.status = PostStatus.OPEN;
        return p;
    }
}