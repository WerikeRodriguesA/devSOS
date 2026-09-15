-- ============================================================================
-- devSOS — V2: refresh_tokens (rotacao de refresh token + revogacao/logout)
-- Aplicada pelo Flyway no boot. Complementa o V1 (usuarios/sessoes).
-- Nota: sem BEGIN/COMMIT — o Flyway roda cada migracao em transacao propria.
--
-- O QUE E / POR QUE
--   O JWT de acesso (V1) e stateless: o servidor nao guarda a sessao, apenas
--   valida a assinatura. Isso impede revoked-lo antes do tempo de vida acabar.
--   Para permitir "logout forcado"/revogacao, introduzimos um TOKEN OPACO de
--   REFRESH com ciclo de vida maior (default 7 dias):
--     * o cliente recebe no login/cadastro um access JWT (curto) + um refresh
--       token (opaco, guardado AQUI);
--     * quando o access expira, o cliente troca o refresh por um access novo
--       (POST /api/auth/refresh) — com ROTACAO: o token usado morre e nasce
--       outro (reuso de token ja revogado = roubo suspeito => derruba tudo);
--     * revogar = marcar revoked_at. Logout revoga 'este dispositivo'
--       (token especifico) ou TODOS os tokens do usuario (logout forcado).
--
-- SEGURANCA
--   * Guardamos unicamente o SHA-256 HEX do token (64 chars), nunca o token
--     cru — vazamento do banco nao libera tokens utilizaveis.
--   * token_hash tem indice UNICO: gift para lookup O(1) no refresh.
--   * replaced_by liga o token ao seu sucessor (rastreabilidade da rotacao).
-- ============================================================================

CREATE TABLE refresh_tokens (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    token_hash   CHAR(64)    NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID,

    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT refresh_tokens_hash_positive CHECK (token_hash <> repeat('0', 64))
);

CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX        idx_refresh_tokens_user ON refresh_tokens (user_id, created_at DESC);

-- Novos tokens do MESMO usuário devem expirar em ordem de criação (mesmo TTL)
COMMENT ON TABLE refresh_tokens IS
    'Refresh token opaco (SHA-256) para renovar o access JWT e permitir revogacao/logout forcado. Rotaciona a cada uso; reuso de token revogado revoga a familia toda.';