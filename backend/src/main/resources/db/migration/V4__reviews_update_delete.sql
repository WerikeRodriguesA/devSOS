-- ============================================================================
-- V4__reviews_update_delete.sql  (issue #20)
-- ============================================================================
-- Até a V1, o trigger `trg_reviews_rating` só rodava em INSERT e a função
-- `fn_recalc_user_rating` lia apenas `NEW.reviewed_id`. A issue #20 trouxe
-- PATCH/DELETE de reviews — a média também precisa recalcular quando:
--   * UPDATE: a nota mudou (4 -> 5) — usa NEW.reviewed_id
--   * DELETE: a review sumiu — usa OLD.reviewed_id
-- A função passou a ser sensível ao TG_OP (INSERT/UPDATE/DELETE) e ganhou um
-- trigger AFTER para cada operação.
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_recalc_user_rating()
RETURNS TRIGGER AS $$
DECLARE
    v_reviewed_id UUID;
BEGIN
    IF TG_OP = 'DELETE' THEN
        v_reviewed_id := OLD.reviewed_id;
    ELSE
        v_reviewed_id := NEW.reviewed_id;
    END IF;

    UPDATE users
       SET media_avaliacoes = COALESCE(
             (SELECT ROUND(AVG(nota)::numeric, 2) FROM reviews
               WHERE reviewed_id = v_reviewed_id), 0)
     WHERE id = v_reviewed_id;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_reviews_rating ON reviews;
CREATE TRIGGER trg_reviews_rating
    AFTER INSERT OR UPDATE OR DELETE ON reviews
    FOR EACH ROW EXECUTE FUNCTION fn_recalc_user_rating();