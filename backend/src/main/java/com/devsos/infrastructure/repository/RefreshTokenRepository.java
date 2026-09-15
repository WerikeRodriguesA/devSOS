package com.devsos.infrastructure.repository;

import com.devsos.domain.refresh.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de {@link RefreshTokenEntity}.
 *
 * <p>O ponto-chave é {@code findByTokenHash}: a busca do refresh é feita pelo
 * hash SHA-256 (o token cru nunca chega ao banco).</p>
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /** Logout forçado: revoga TODOS os refresh tokens ativos do usuário. */
    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revokedAt = :agora WHERE r.userId = :userId AND r.revokedAt IS NULL")
    int revogarTodosDoUsuario(@Param("userId") UUID userId, @Param("agora") Instant agora);

    /** Higiene: apaga tokens já expirados (acumulou vencimento → vira lixo). */
    void deleteByExpiresAtBefore(Instant agora);
}