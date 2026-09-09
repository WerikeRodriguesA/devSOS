package com.devsos.domain.post;

/**
 * Ciclo de vida de uma publicação ("socorro").
 * <p>
 * A ordem no domínio é sempre:
 * {@code OPEN → IN_PROGRESS → RESOLVED} (ou {@code CANCELLED}).
 * A transição é controlada na camada de serviço/regra de negócio.
 */
public enum PostStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CANCELLED
}