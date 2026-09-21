-- ============================================================================
-- devSOS — V5: password_reset_tokens (recuperacao de senha) + limpeza do
-- placeholder de senha legado.
-- Aplicada pelo Flyway no boot. Complementa V1 (users), V2 (refresh_tokens).
-- (V4 reservada para reviews update/delete.)
-- Sem BEGIN/COMMIT — o Flyway roda cada migracao em transacao propria.
--
-- O QUE E / POR QUE
--   Ate aqui a conta so tinha "criar + entrar": quem esquecia a senha ficava
--   travado (inclusive usuarios legados que receberam um hash placeholder
--   nao-loggavel na V1). Esta tabela guarda os tokens de recuperacao:
--     * POST /api/auth/forgot-password gera um token opaco de vida curta
--       (default 30 min) e "envia por e-mail" (fase 1: loga no console);
--     * POST /api/auth/reset-password troca esse token por uma senha nova;
--     * o token e de USO UNICO (used_at) e expira sozinho (expires_at).
--
-- SEGURANCA
--   * Guardamos apenas o SHA-256 HEX (64 chars) do token cru, nunca o token —
--     vazamento do banco nao libera recuperacao de conta.
--   * token_hash tem indice UNICO (lookup O(1) e impossibilita colisao).
--   * Ao redefinir a senha, o service revoga TODOS os refresh tokens do
--     usuario (sessoes antigas caem).
-- ============================================================================

CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,

    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_password_reset_tokens_hash ON password_reset_tokens (token_hash);
CREATE INDEX        idx_password_reset_tokens_user ON password_reset_tokens (user_id, created_at DESC);

COMMENT ON TABLE password_reset_tokens IS
    'Token opaco (SHA-256) de recuperacao de senha: uso unico (used_at) e expiracao curta (expires_at).';

-- Remove o DEFAULT placeholder da V1 ('$2a$10$placeholder'): ele nao e um
-- hash BCrypt valido e permitia linhas "sem senha real". A partir de agora o
-- password_hash e SEMPRE informado explicitamente pelo codigo (cadastro ou
-- reset). Usuarios legados presos no placeholder voltam via o fluxo oficial
-- de recuperacao (forgot/reset-password).
ALTER TABLE users ALTER COLUMN password_hash DROP DEFAULT;
