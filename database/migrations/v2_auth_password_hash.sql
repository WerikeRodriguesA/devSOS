-- ============================================================================
-- devSOS — Migração v2: autenticação JWT
-- ============================================================================
-- Adiciona o campo password_hash à tabela users (BCrypt).
-- NOT NULL é aplicado numa segunda passada: usuários legados (ex.: Ana Dev,
-- criados manualmente) recebem uma senha provisória não-loggável primeiro.
-- ============================================================================

BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(100);

-- Senha provisória (hash de um valor inutilizável) para usuários legados,
-- para a coluna poder virar NOT NULL sem quebrar linhas existentes.
-- Não é uma senha válida: ninguém consegue logar com ela.
UPDATE users
   SET password_hash = COALESCE(password_hash, '$2a$10$uNelXXi0knIcQHTB9VjZJeLWxwQzAfEnA2ixwG8GQnHq1VlXvV9ly')
 WHERE password_hash IS NULL;

ALTER TABLE users
    ALTER COLUMN password_hash SET NOT NULL;

COMMIT;