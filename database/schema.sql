-- ============================================================================
-- devSOS — Rede social de ajuda entre desenvolvedores
-- Script de criação do banco de dados (DDL) — PostgreSQL 13+
-- ============================================================================
-- Projeto: feed estilo Instagram + dinâmica de chamadas (Uber)
-- Autor  : devSOS Team
-- ----------------------------------------------------------------------------
-- ESCOPO
--   1. Tabela users   : cadastro dos devs (quem pede e quem ajuda)
--   2. Tabela posts   : os "problemas" publicados no feed
--   3. Tabela sessions: a "corrida" — vínculo entre post, helper e chat
--   4. Tabela reviews : avaliação mútua após uma sessão concluída
-- ----------------------------------------------------------------------------
-- CONVENÇÕES
--   * Todas as PKs são UUID geradas no banco (gen_random_uuid()).
--   * Enum-like colunas usam TEXT + CHECK (flexível e fácil de migrar).
--   * Timestamps: sempre TIMESTAMPTZ (com fuso horário) = UTC.
-- ============================================================================

BEGIN;

-- ============================================================================
-- EXTENSÃO NECESSÁRIA: geração de UUID
-- (PostgreSQL 13+: usa o gen_random_uuid() nativo, da pgcrypto-core)
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- 1) TABELA users
-- ============================================================================
CREATE TABLE users (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome                     VARCHAR(120)     NOT NULL,
    email                    VARCHAR(255)     NOT NULL,
    password_hash            VARCHAR(100)     NOT NULL DEFAULT '$2a$10$placeholder',
    bio                      TEXT             NOT NULL DEFAULT '',
    github_username          VARCHAR(60)      NOT NULL DEFAULT '',
    avatar_url               TEXT             NOT NULL DEFAULT '',
    saldo_pontos             INTEGER          NOT NULL DEFAULT 0,
    media_avaliacoes         NUMERIC(3, 2)    NOT NULL DEFAULT 0,
    tecnologias_dominadas    TEXT[]           NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_at               TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ      NOT NULL DEFAULT now(),

    CONSTRAINT users_email_uniq    UNIQUE (email),
    CONSTRAINT users_github_uniq   UNIQUE (github_username),
    CONSTRAINT users_saldo_check   CHECK (saldo_pontos >= 0),
    CONSTRAINT users_avaliacao_chk CHECK (media_avaliacoes >= 0 AND media_avaliacoes <= 5)
);

-- Índices para login/pesquisa e tecnologia (GIN para arranjar arrays)
CREATE INDEX idx_users_email       ON users (email);
CREATE INDEX idx_users_github      ON users (github_username);
CREATE INDEX idx_users_media_av    ON users (media_avaliacoes DESC);
CREATE INDEX idx_users_tecnologias ON users USING GIN (tecnologias_dominadas);

-- ============================================================================
-- 2) TABELA posts
-- ============================================================================
CREATE TABLE posts (
    id             UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id      UUID              NOT NULL,
    titulo         VARCHAR(160)      NOT NULL,
    descricao      TEXT              NOT NULL,
    media_url      TEXT              NOT NULL DEFAULT '',
    tags           TEXT[]            NOT NULL DEFAULT ARRAY[]::TEXT[],
    tipo           TEXT              NOT NULL DEFAULT 'FREE',
    recompensa_valor NUMERIC(10, 2)  NOT NULL DEFAULT 0,
    status         TEXT              NOT NULL DEFAULT 'OPEN',
    created_at     TIMESTAMPTZ       NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ       NOT NULL DEFAULT now(),

    CONSTRAINT posts_tipo_check   CHECK (tipo IN ('FREE', 'PAID')),
    CONSTRAINT posts_status_check CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED')),
    CONSTRAINT posts_recompensa_chk CHECK (recompensa_valor >= 0),
    CONSTRAINT posts_clean_reward CHECK (tipo = 'FREE' OR recompensa_valor > 0),

    CONSTRAINT fk_posts_author FOREIGN KEY (author_id)
        REFERENCES users (id) ON DELETE CASCADE
);

-- Índices: feed recente, feed por autor, filtros por recompensa/tags
CREATE INDEX idx_posts_feed           ON posts (status, created_at DESC);
CREATE INDEX idx_posts_status         ON posts (status);
CREATE INDEX idx_posts_author         ON posts (author_id, created_at DESC);
CREATE INDEX idx_posts_recompensa     ON posts (recompensa_valor DESC) WHERE tipo = 'PAID';
CREATE INDEX idx_posts_tags           ON posts USING GIN (tags);
CREATE INDEX idx_posts_status_tipo    ON posts (status, tipo, created_at DESC);

-- ============================================================================
-- 3) TABELA sessions  (a "corrida" / chamada de ajuda)
-- ============================================================================
CREATE TABLE sessions (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id        UUID         NOT NULL,
    helper_id      UUID         NOT NULL,
    status         TEXT         NOT NULL DEFAULT 'MATCHED',
    chat_room_id   UUID         NOT NULL DEFAULT gen_random_uuid(),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ,

    CONSTRAINT sessions_status_check  CHECK (status IN ('MATCHED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT fk_sessions_post   FOREIGN KEY (post_id)   REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_sessions_helper FOREIGN KEY (helper_id) REFERENCES users (id) ON DELETE CASCADE
);

COMMENT ON TABLE sessions IS
    'A "corrida": vincula um post a um helper. O helper não pode ser o próprio autor do post (regra aplicada por trigger: trg_sessions_no_self_help).';

-- Índices: fila de "MATCHED" disponível, histórico do helper/post
CREATE INDEX idx_sessions_fila      ON sessions (status, created_at) WHERE status = 'MATCHED';
CREATE INDEX idx_sessions_post      ON sessions (post_id);
CREATE INDEX idx_sessions_helper    ON sessions (helper_id, created_at DESC);
CREATE INDEX idx_sessions_chat      ON sessions (chat_room_id);
CREATE INDEX idx_sessions_status    ON sessions (status);

-- Índice único: "uma corrida por vez" — 1 post só pode ter 1 sessão MATCHED
-- ou ACTIVE. Completei/Cancelled saem do índice, permitindo nova fila depois
-- de cancelamento.
CREATE UNIQUE INDEX uniq_sessions_post_active ON sessions (post_id) WHERE status IN ('MATCHED', 'ACTIVE');

-- ============================================================================
-- 4) TABELA reviews
-- ============================================================================
CREATE TABLE reviews (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID          NOT NULL,
    reviewer_id UUID          NOT NULL,
    reviewed_id UUID          NOT NULL,
    nota        SMALLINT      NOT NULL CHECK (nota BETWEEN 1 AND 5),
    comentario  TEXT          NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT reviews_um_por_sessao UNIQUE (session_id, reviewer_id),
    CONSTRAINT reviews_avaliador_diff CHECK (reviewer_id <> reviewed_id),
    CONSTRAINT fk_reviews_session  FOREIGN KEY (session_id)  REFERENCES sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewed FOREIGN KEY (reviewed_id) REFERENCES users (id)    ON DELETE CASCADE
);

-- Índices: histórico de avaliações recebidas, média por avaliação
CREATE INDEX idx_reviews_reviewed  ON reviews (reviewed_id, created_at DESC);
CREATE INDEX idx_reviews_reviewer  ON reviews (reviewer_id);
CREATE INDEX idx_reviews_session   ON reviews (session_id);

-- ============================================================================
-- TRIGGERS: atualização automática de updated_at (+ média de avaliações)
-- ============================================================================

-- Função genérica: mantém updated_at atualizado
CREATE OR REPLACE FUNCTION fn_touch_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_posts_touch     BEFORE UPDATE ON posts     FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
CREATE TRIGGER trg_users_touch     BEFORE UPDATE ON users     FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
CREATE TRIGGER trg_sessions_touch  BEFORE UPDATE ON sessions  FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();

-- Função: garante que o dono do post não "aceite o socorro" do próprio problema
CREATE OR REPLACE FUNCTION fn_no_self_help()
RETURNS TRIGGER AS $$
DECLARE
    v_author_id UUID;
BEGIN
    SELECT author_id INTO v_author_id FROM posts WHERE id = NEW.post_id;
    IF NEW.helper_id = v_author_id THEN
        RAISE EXCEPTION 'O autor do post não pode aceitar o próprio pedido de ajuda (post_id=%, helper_id=%)', NEW.post_id, NEW.helper_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sessions_no_self_help BEFORE INSERT ON sessions
    FOR EACH ROW EXECUTE FUNCTION fn_no_self_help();

-- Função: recalcula a média de avaliações do usuário quando nasce review
CREATE OR REPLACE FUNCTION fn_recalc_user_rating()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE users
       SET media_avaliacoes = COALESCE(
             (SELECT ROUND(AVG(nota)::numeric, 2) FROM reviews WHERE reviewed_id = NEW.reviewed_id), 0)
     WHERE id = NEW.reviewed_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reviews_rating AFTER INSERT ON reviews
    FOR EACH ROW EXECUTE FUNCTION fn_recalc_user_rating();

-- ============================================================================
-- FIM DO SCRIPT
-- ============================================================================
COMMIT;