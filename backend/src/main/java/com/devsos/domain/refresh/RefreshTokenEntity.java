package com.devsos.domain.refresh;

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

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade JPA da tabela {@code refresh_tokens} — o "outro lado" da
 * autenticação JWT.
 *
 * <h2>Por que um refresh token existe?</h2>
 * O access JWT é curto e <b>stateless</b>: ele pode ser validado por qualquer
 * instância sem consultar o banco, mas ninguém consegue "revogá-lo" antes de
 * expirar. O refresh token (longo e <b>guardado aqui no banco</b>) é a peça
 * que permite:
 * <ul>
 *   <li>renovar o access JWT sem pedir a senha de novo ({@code /api/auth/refresh});</li>
 *   <li><b>revogar</b> o acesso (logout / logout forçado) marcando
 *       {@code revokedAt}.</li>
 * </ul>
 *
 * <h2>O que NUNCA fica aqui</h2>
 * Apenas o <b>hash SHA-256</b> (hex, 64 chars) do token cru — o token em si
 * só existe na resposta do login/refresh e no cliente. Um vazamento do banco
 * não libera tokens utilizáveis.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    /**
     * Cria um token de refresh já pronto para persistir.
     *
     * @param userId    dono do token (extraído do JWT, nunca do pedido)
     * @param tokenHash hash SHA-256 (hex) do token opaco gerado pelo serviço
     * @param ttl       vida útil do token em segundos
     */
    public RefreshTokenEntity(UUID userId, String tokenHash, long ttlSegundos) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = Instant.now().plusSeconds(ttlSegundos);
    }

    /** Marca o token como revogado — ele passa a ser inutilizável. */
    public void revogar(UUID substitutoId) {
        this.revokedAt = Instant.now();
        this.replacedBy = substitutoId;
    }

    public boolean isRevogado() {
        return revokedAt != null;
    }

    public boolean estaExpirado() {
        return Instant.now().isAfter(expiresAt);
    }
}