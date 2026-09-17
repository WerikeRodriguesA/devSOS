-- ============================================================================
-- devSOS — V3: busca e filtros no feed
-- Aplicada pelo Flyway no boot. Complementa o V1 com suporte a PERFORMANCE
-- para os novos query params de GET /api/posts: q (texto), tag e tipo.
--
-- O QUE E / POR QUE
--   * q busca com ILIKE '%q%' (contém, case-insensitive) em titulo e descricao:
--     um índice btree comum NÃO ajuda — o curinga na frente impede o range scan.
--     O pg_trgm fornece índice GIN com "gin_trgm_ops", que indexa trigramas e
--     acelera exatamente esse padrão de busca textual. Bem maior que o btree,
--     mas é o "trigger" certo para busca de texto.
--   * tags já tem índice GIN (idx_posts_tags) do V1 — o filtro repete o mesmo
--     operador de contenção (@>) que o GIN indexa.
--   * tipo já é coberto pelo idx_posts_status_tipo (status, tipo, created_at).
--
-- ATENCAO (seguranca/producao):
--   CREATE EXTENSION pg_trgm exige privilégio de superuser (ou role com
--   CREATE). No devsos-db local (postgres) não tem problema. Em produção,
--   garantir que a role da aplicação consiga criar a extensão.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Busca textual: trigramas de titulo e descricao (lowercase p/ casar com ILIKE)
CREATE INDEX idx_posts_titulo_trgm   ON posts USING GIN (lower(titulo) gin_trgm_ops);
CREATE INDEX idx_posts_descricao_trgm ON posts USING GIN (lower(descricao) gin_trgm_ops);

COMMENT ON INDEX idx_posts_titulo_trgm IS
    'Acelera ILIKE %q% no titulo (pg_trgm GIN) — usado pelo ?q do GET /api/posts.';
COMMENT ON INDEX idx_posts_descricao_trgm IS
    'Acelera ILIKE %q% na descricao (pg_trgm GIN) — usado pelo ?q do GET /api/posts.';