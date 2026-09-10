-- ============================================================================
-- devSOS — Migração v3: "uma corrida por vez" por post
-- ============================================================================
-- Ativa o índice único parcial que estava PENDENTE de decisão na modelagem:
-- um post só pode ter 1 corrida MATCHED ou ACTIVE. O segundo helper que
-- tentar aceitar um post já reservado é barrado pelo BANCO (além da validação
-- no service). Cancelled/Completed não entram no índice, então o post pode
-- voltar à fila depois de uma corrida cancelada.
-- ============================================================================

BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_sessions_post_active
    ON sessions (post_id)
    WHERE status IN ('MATCHED', 'ACTIVE');

COMMIT;