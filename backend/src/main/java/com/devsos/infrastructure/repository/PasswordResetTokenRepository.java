package com.devsos.infrastructure.repository;

import com.devsos.domain.passwordreset.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de {@link PasswordResetTokenEntity}.
 *
 * <p>A busca é pelo {@code SHA-256} do token (o token cru nunca chega ao
 * banco), como no {@link RefreshTokenRepository}.</p>
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    /** Invalida os tokens pendentes do usuário (ex.: após uma redefinição). */
    @Modifying
    @Query("UPDATE PasswordResetTokenEntity t SET t.usedAt = :agora WHERE t.userId = :userId AND t.usedAt IS NULL")
    int invalidarTodosDoUsuario(@Param("userId") UUID userId, @Param("agora") Instant agora);

    /** Higiene: apaga tokens já expirados. */
    void deleteByExpiresAtBefore(Instant agora);
}
