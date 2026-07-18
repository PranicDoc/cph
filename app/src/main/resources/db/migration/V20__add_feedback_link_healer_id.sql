-- =============================================================================
-- V20__add_feedback_link_healer_id.sql
-- Phase 26 (clinic removal, application-layer cutover) plan 11: D-26-04's
-- SELF_REG half. Adds a nullable healer_id UUID column to feedback_link and
-- backfills it from healing_case via the existing case_id join, so the
-- WhatsApp feedback pipeline's SELF_REG token can carry a real owning-healer
-- id instead of the retiring clinic_id.
--
-- feedback_link is a standalone entity (NOT @Audited, per FeedbackLink's own
-- javadoc/entity shape — no feedback_link_aud table exists) — no _aud
-- counterpart statement is needed here, unlike V17/V19's Envers-table parity
-- requirement.
--
-- ADDITIVE ONLY: healer_id is nullable in this migration (matches the
-- pre-existing clinic_id NOT NULL shape's staged-constraint precedent — no
-- NOT NULL tightening here). clinic_id is NOT dropped here; that is 26-12's
-- job alongside the rest of the phase's DROP wave.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- DDL: feedback_link — nullable healer_id + named FK + index
-- -----------------------------------------------------------------------------

ALTER TABLE feedback_link
    ADD COLUMN healer_id UUID,
    ADD CONSTRAINT fk_feedback_link_healer FOREIGN KEY (healer_id) REFERENCES healer(id);

CREATE INDEX idx_feedback_link_healer_id ON feedback_link(healer_id);

-- -----------------------------------------------------------------------------
-- Backfill: feedback_link.healer_id <- healing_case.healer_id (single-hop
-- join via case_id; healing_case.healer_id is the phase's root attribution
-- column, backfilled since Phase 25). Idempotency-guarded, safely re-runnable.
-- -----------------------------------------------------------------------------

UPDATE feedback_link fl
    SET healer_id = hc.healer_id
    FROM healing_case hc
    WHERE fl.case_id = hc.id
      AND fl.healer_id IS NULL;
