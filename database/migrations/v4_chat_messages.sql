-- ============================================================================
-- v4 — Chat em tempo real: tabela chat_messages
-- ----------------------------------------------------------------------------
-- O que faz: cria a tabela que PERSISTE as mensagens das salas de chat
-- (a sala em si já existe: sessions.chat_room_id é gerado no aceite do socorro).
--
-- POR QUE session_id E chat_room_id?
--   * chat_room_id  → usado pelo WebSocket para rotear mensagens ao tópico
--                     (/topic/chat/{chat_room_id}) — uma coluna "denormalizada"
--                     de conveniência para a busca por sala.
--   * session_id    → FK de integridade: vincula a mensagem à corrida (que tem
--                     autor + helper). É POR ela que autorizamos "quem pode ler
--                     o histórico" (participante pode, terceiro não).
--
-- POR QUE sender_id é FK para users(id)?
--   Para nunca existir mensagem de um usuário apagado/inventado.
--
-- POR QUE o tipo é CHECK, e não enum nativo do Postgres?
--   Seguindo a convenção do schema (users.status, sessions.status): TEXT + CHECK
--   é mais simples de migrar e mais legível no banco.
-- ============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS chat_messages (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id   UUID        NOT NULL,
    chat_room_id UUID        NOT NULL,
    sender_id    UUID        NOT NULL,
    tipo         TEXT        NOT NULL DEFAULT 'CHAT'
                             CHECK (tipo IN ('CHAT', 'CODE_SNIPPET', 'JOIN', 'LEAVE')),
    conteudo     TEXT        NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_chat_messages_session FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender  FOREIGN KEY (sender_id)  REFERENCES users (id)    ON DELETE CASCADE
);

-- Buscas por sala (isto é, por corrida) em ordem cronológica para o histórico
CREATE INDEX IF NOT EXISTS idx_chat_room ON chat_messages (chat_room_id, created_at);
-- Filtro por corrida (para o endpoint de histórico por session)
CREATE INDEX IF NOT EXISTS idx_chat_session ON chat_messages (session_id, created_at);

COMMIT;