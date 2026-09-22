package com.devsos.domain.passwordreset;

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
 * Entidade JPA da tabela {@code password_reset_tokens} — o token de
 * <b>recuperação de senha</b> (fluxo "esqueci minha senha").
 *
 * <h2>Regras de vida</h2>
 * <ul>
 *   <li><b>Uso único:</b> ao redefinir a senha, {@code used_at} é marcado e o
 *       token não serve mais ({@link #isUsado()}).</li>
 *   <li><b>Expiração curta:</b> por padrão 30 min ({@link #estaExpirado()}).</li>
 *   <li><b>Só o hash:</b> como no refresh token, guardamos apenas o
 *       {@code SHA-256} hex do token cru — vazamento do banco não libera
 *       recuperação de conta.</li>
 * </ul>
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetTokenEntity {

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

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    /**
     * Cria um token de recuperação pronto para persistir.
     *
     * @param userId      dono do token
     * @param tokenHash   hash SHA-256 (hex) do token opaco
     * @param ttlSegundos vida útil em segundos
     */
    public PasswordResetTokenEntity(UUID userId, String tokenHash, long ttlSegundos) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = Instant.now().plusSeconds(ttlSegundos);
    }

    /** Consome o token (uso único). */
    public void usar() {
        this.usedAt = Instant.now();
    }

    public boolean isUsado() {
        return usedAt != null;
    }

    public boolean estaExpirado() {
        return Instant.now().isAfter(expiresAt);
    }
}
