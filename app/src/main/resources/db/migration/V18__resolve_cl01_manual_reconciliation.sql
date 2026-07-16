-- =============================================================================
-- V18__resolve_cl01_manual_reconciliation.sql
-- Phase 25 (clinic removal, wave 2): resolves the 8 CL01 reconciliation-list
-- rows from Phase 24's audit that V17's automated backfill could not reach
-- (5 patients with NULL created_by; 3 healing_case rows with a dangling
-- healer_id FK), using human-reviewed per-UUID targets recorded in
-- 25-02-RESOLUTION-DECISIONS.md. No heuristic (email-match, "earliest
-- healer", or any join) is used for these 8 rows — every target below is a
-- literal, human-confirmed value.
--
-- Decision (25-02-RESOLUTION-DECISIONS.md, default-primary option): all 8
-- rows are assigned to CL01's primary healer 019f18a3-1c4c-71e5-a0e9-799c8fdc20f7.
--
-- After correcting the 3 case healer_ids, this migration re-propagates the
-- correction down the same V17 join chain so no descendant session/scan/
-- reading/protocol row keeps one of the 3 now-stale dangling values.
--
-- Catalog decision (25-02-RESOLUTION-DECISIONS.md): "none" — chakra_catalog
-- and prana_color_catalog hold only system-level seed data, no clinic-custom
-- rows. No UPDATE is written for either table; healer_id stays NULL there,
-- which is the accepted, recorded state (schema parity only, added by V17).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 5 named patient UPDATEs (healer_id currently NULL, created_by also NULL —
-- no signal to derive from, target is the human-confirmed CL01 primary healer)
-- -----------------------------------------------------------------------------

-- Patient 019f18cb-e897-7b3f-84f4-18a3f29f324f -> 019f18a3-1c4c-71e5-a0e9-799c8fdc20f7
UPDATE patient SET healer_id = '019f18a3-1c4c-71e5-a0e9-799c8fdc20f7'
WHERE id = '019f18cb-e897-7b3f-84f4-18a3f29f324f' AND healer_id IS NULL;

-- Patient 019f18eb-5d87-76e1-b5a3-ef9d8d96d381 -> 019f18a3-1c4c-71e5-a0e9-799c8fdc20f7
UPDATE patient SET healer_id = '019f18a3-1c4c-71e5-a0e9-799c8fdc20f7'
WHERE id = '019f18eb-5d87-76e1-b5a3-ef9d8d96d381' AND healer_id IS NULL;

-- Patient 019f37b2-9e70-76f0-9307-36142a9b10ec -> 019f18a3-1c4c-71e5-a0e9-799c8fdc20f7
UPDATE patient SET healer_id = '019f18a3-1c4c-71e5-a0e9-799c8fdc20f7'
WHERE id = '019f37b2-9e70-76f0-9307-36142a9b10ec' AND healer_id IS NULL;

-- Patient 019f4bea-2183-7d17-a772-8261ad6515e6 -> 019f18a3-1c4c-71e5-a0e9-799c8fdc20f7
UPDATE patient SET healer_id = '019f18a3-1c4c-71e5-a0e9-799c8fdc20f7'
WHERE id = '019f4bea-2183-7d17-a772-8261ad6515e6' AND healer_id IS NULL;

-- Patient 019f64b8-6f8b-7343-a1d7-6b435f6685c1 -> 019f18a3-1c4c-71e5-a0e9-799c8fdc20f7
UPDATE patient SET healer_id = '019f18a3-1c4c-71e5-a0e9-799c8fdc20f7'
WHERE id = '019f64b8-6f8b-7343-a1d7-6b435f6685c1' AND healer_id IS NULL;

-- -----------------------------------------------------------------------------
-- 3 named case UPDATEs (healer_id is NOT NULL by schema but currently dangling
-- — no "AND healer_id IS NULL" guard here since these are corrections, not
-- fills; guard instead on the exact known-dangling value being replaced)
-- -----------------------------------------------------------------------------

-- Case 019f18cc-4257-72ff-8001-4d00617d6f35: dangling daad34c7-7836-4972-a4bd-b72573269576 -> 019f18a3-1c4c-71e5-a0e9-799c8fdc20f7
UPDATE healing_case SET healer_id = '019f18a3-1c4c-71e5-a0e9-799c8fdc20f7'
WHERE id = '019f18cc-4257-72ff-8001-4d00617d6f35' AND healer_id = 'daad34c7-7836-4972-a4bd-b72573269576';

-- Case 019f18eb-9009-7045-b428-38fbd6470bae: dangling 47f444d7-82cb-4fef-bba1-6aa10b7e0d3d -> 019f18a3-1c4c-71e5-a0e9-799c8fdc20f7
UPDATE healing_case SET healer_id = '019f18a3-1c4c-71e5-a0e9-799c8fdc20f7'
WHERE id = '019f18eb-9009-7045-b428-38fbd6470bae' AND healer_id = '47f444d7-82cb-4fef-bba1-6aa10b7e0d3d';

-- Case 019f37b2-e7c5-72cc-ab21-5dc6ae98d5cc: dangling 7037a3a6-a01d-4c0f-8e22-850ceda90d11 -> 019f18a3-1c4c-71e5-a0e9-799c8fdc20f7
UPDATE healing_case SET healer_id = '019f18a3-1c4c-71e5-a0e9-799c8fdc20f7'
WHERE id = '019f37b2-e7c5-72cc-ab21-5dc6ae98d5cc' AND healer_id = '7037a3a6-a01d-4c0f-8e22-850ceda90d11';

-- -----------------------------------------------------------------------------
-- re-propagate corrected CL01 case healer_ids to descendants
-- Mirrors V17's step-1/3-8 join chain, but the idempotency guard is changed
-- from "AND x.healer_id IS NULL" to "AND x.healer_id IN (<3 dangling values>)"
-- so it only ever touches rows still carrying a stale dangling value from one
-- of the 3 corrected cases above, and is safely re-runnable.
-- -----------------------------------------------------------------------------

-- healing_session <- healing_case (direct FK: healing_case_id)
UPDATE healing_session s
    SET healer_id = hc.healer_id
    FROM healing_case hc
    WHERE s.healing_case_id = hc.id
      AND s.healer_id IN ('daad34c7-7836-4972-a4bd-b72573269576','47f444d7-82cb-4fef-bba1-6aa10b7e0d3d','7037a3a6-a01d-4c0f-8e22-850ceda90d11');

-- case_closure <- healing_case (direct FK: healing_case_id)
UPDATE case_closure cc
    SET healer_id = hc.healer_id
    FROM healing_case hc
    WHERE cc.healing_case_id = hc.id
      AND cc.healer_id IN ('daad34c7-7836-4972-a4bd-b72573269576','47f444d7-82cb-4fef-bba1-6aa10b7e0d3d','7037a3a6-a01d-4c0f-8e22-850ceda90d11');

-- chakra_scan <- healing_session (1-hop, FK: healing_session_id; must run after healing_session re-propagation above)
UPDATE chakra_scan cs
    SET healer_id = hs.healer_id
    FROM healing_session hs
    WHERE cs.healing_session_id = hs.id
      AND cs.healer_id IN ('daad34c7-7836-4972-a4bd-b72573269576','47f444d7-82cb-4fef-bba1-6aa10b7e0d3d','7037a3a6-a01d-4c0f-8e22-850ceda90d11');

-- protocol_entry <- healing_session (1-hop, FK: healing_session_id; must run after healing_session re-propagation above)
UPDATE protocol_entry pe
    SET healer_id = hs.healer_id
    FROM healing_session hs
    WHERE pe.healing_session_id = hs.id
      AND pe.healer_id IN ('daad34c7-7836-4972-a4bd-b72573269576','47f444d7-82cb-4fef-bba1-6aa10b7e0d3d','7037a3a6-a01d-4c0f-8e22-850ceda90d11');

-- symptom_feedback <- healing_session (1-hop, FK: healing_session_id; must run after healing_session re-propagation above)
UPDATE symptom_feedback sf
    SET healer_id = hs.healer_id
    FROM healing_session hs
    WHERE sf.healing_session_id = hs.id
      AND sf.healer_id IN ('daad34c7-7836-4972-a4bd-b72573269576','47f444d7-82cb-4fef-bba1-6aa10b7e0d3d','7037a3a6-a01d-4c0f-8e22-850ceda90d11');

-- qualitative_feedback <- healing_session (1-hop, FK: healing_session_id; must run after healing_session re-propagation above)
UPDATE qualitative_feedback qf
    SET healer_id = hs.healer_id
    FROM healing_session hs
    WHERE qf.healing_session_id = hs.id
      AND qf.healer_id IN ('daad34c7-7836-4972-a4bd-b72573269576','47f444d7-82cb-4fef-bba1-6aa10b7e0d3d','7037a3a6-a01d-4c0f-8e22-850ceda90d11');

-- chakra_reading <- chakra_scan (2-hop, FK: chakra_scan_id; must run after chakra_scan re-propagation above)
UPDATE chakra_reading cr
    SET healer_id = cs.healer_id
    FROM chakra_scan cs
    WHERE cr.chakra_scan_id = cs.id
      AND cr.healer_id IN ('daad34c7-7836-4972-a4bd-b72573269576','47f444d7-82cb-4fef-bba1-6aa10b7e0d3d','7037a3a6-a01d-4c0f-8e22-850ceda90d11');

-- protocol_color <- protocol_entry (2-hop, FK: protocol_entry_id; must run after protocol_entry re-propagation above)
UPDATE protocol_color pc
    SET healer_id = pe.healer_id
    FROM protocol_entry pe
    WHERE pc.protocol_entry_id = pe.id
      AND pc.healer_id IN ('daad34c7-7836-4972-a4bd-b72573269576','47f444d7-82cb-4fef-bba1-6aa10b7e0d3d','7037a3a6-a01d-4c0f-8e22-850ceda90d11');

-- -----------------------------------------------------------------------------
-- Catalog backfill: none. chakra_catalog / prana_color_catalog hold only
-- system seed data per 25-02-RESOLUTION-DECISIONS.md; no clinic-custom rows
-- exist, so no UPDATE is required. healer_id remains NULL on both tables —
-- this is the accepted, recorded state (schema parity only, added by V17).
-- -----------------------------------------------------------------------------
