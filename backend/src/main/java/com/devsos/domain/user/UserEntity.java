package com.devsos.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * Entidade JPA que representa a tabela {@code users}.
 *
 * <h2>POR QUE NÃO USAR {@code @Data} EM ENTIDADE JPA?</h2>
 * O Lombok {@code @Data} gera 5 coisas de uma vez: getters, setters,
 * {@code equals()}, {@code hashCode()} e {@code toString()}. Em entidades JPA
 * isso é perigoso:
 * <ul>
 *   <li><b>{@code equals/hashCode} em coleções:</b> o Hibernate usa coleções
 *       lazy (ex.: {@code tecnologias_dominadas}); um {@code hashCode} que
 *       itera a lista pode disparar consultas mesmo sem você querer, ou pior,
 *       quebrar associações {@code Set}.</li>
 *   <li><b>{@code toString()} infinito:</b> com a associação {@code Post.author}
 *       o {@code toString} de uma entidade chamaria o da outra
 *       (e vice-versa) → {@code StackOverflowError}.</li>
 *   <li><b>Controle fino:</b> separando {@code @Getter} + {@code @Setter}
 *       podemos, por exemplo, não expor setter de {@code id} e esconder o uso
 *       de coleções com {@code toImmutableList()}.</li>
 * </ul>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

    /**
     * Você notou que não há setter para {@code id}?
     * O Lombok {@code @Setter} está no nível de CLASSE; para proteger o ID
     * usamos {@code @Setter(AccessLevel.NONE)} no campo. O id só é gerado
     * pelo Hibernate ({@code GenerationType.UUID}).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nome", length = 120, nullable = false)
    private String nome;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "bio")
    private String bio;

    @Column(name = "github_username")
    private String githubUsername;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "saldo_pontos", nullable = false)
    private Integer saldoPontos;

    @Column(name = "media_avaliacoes", nullable = false, precision = 3, scale = 2)
    private BigDecimal mediaAvaliacoes;

    /**
     * Backup de {@code TEXT[]} no PostgreSQL.
     * <p>
     * {@code @JdbcTypeCode(SqlTypes.ARRAY)} é a forma moderna (Hibernate 6+)
     * de mapear um {@code List<String>} Java para um array nativo do banco.
     * Alternativa legada: {@code @ElementCollection} cria UMA TABELA EXTRA —
     * justamente o que quisemos evitar na modelagem.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tecnologias_dominadas", columnDefinition = "text[]")
    private List<String> tecnologiasDominadas = new ArrayList<>();

    /**
     * Timestamps preenchidos pelo banco (DEFAULT now() na DDL) e mantidos
     * consistentes pelo Hibernate/teria que ser com {@code @UpdateTimestamp}.
     * Na nossa DDL quem atualiza {@code updated_at} é um TRIGGER do banco —
     * então a JPA "escuta" e lê o valor de volta após INSERT/UPDATE.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    /**
     * Idem {@code PostEntity}: timestamps delegados ao banco
     * ({@code DEFAULT now()} no INSERT, trigger no UPDATE); o Hibernate relê
     * a linha após o flush.
     */
    @CreationTimestamp
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    /**
     * Construtor mínimo de domínio — quem cria um usuário não precisa saber
     * de id/timestamps (o Hibernate cuida disso). Por isso o padrão é usar
     * um construtor estático/factory ou um constructores explícito e pensar
     * o construtor como "regra de negócio".
     */
    public UserEntity(String nome, String email, String githubUsername) {
        this.nome = nome;
        this.email = email;
        this.githubUsername = githubUsername;
        this.bio = "";
        this.avatarUrl = "";
        this.saldoPontos = 0;
        this.mediaAvaliacoes = BigDecimal.ZERO;
        this.tecnologiasDominadas = new ArrayList<>();
    }
}