-- INFRA-01 (D-24): supporting indexes for the bounded dashboard/session/PDF
-- query paths fixed in this phase. Plain CREATE INDEX per-statement (no
-- CONCURRENTLY) — none of these tables are confirmed hot enough to justify
-- the extra operational complexity of concurrent index builds.

-- CaseRepository.findWithSessionsById (CaseService.buildAndEnqueuePdfJob,
-- the PDF-generation snapshot path) fetch-joins healing_session by
-- healing_case_id — already covered by V0's idx_healing_session_case_id,
-- listed here only for completeness of the reasoning, no new index needed.

-- pdfJobRepository.findTopByHealingCaseIdOrderByCreatedAtDesc runs on every
-- postMessageToGeneratePdf call (existing-job branch check) — no index
-- currently covers pdf_job.healing_case_id, forcing a full scan ordered by
-- created_at per call.
CREATE INDEX idx_pdf_job_healing_case_created
    ON pdf_job (healing_case_id, created_at DESC);

-- HealerProtocolUnderstandingRepository.findByHealingCaseId is called once
-- per PDF generation (hoisted out of the per-session loop this phase, see
-- CphCaseMapper.toSortedSessions) — no index currently covers
-- healer_protocol_understanding.healing_case_id beyond V4's plain btree
-- (idx_hpu_healing_case already exists); listed here only for completeness.

-- ChakraScan / SymptomFeedback / QualitativeFeedback are all loaded per
-- session via hibernate.default_batch_fetch_size batching (application.yaml)
-- — each already has a covering index on healing_session_id from V0
-- (idx_chakra_scan_session_id, idx_symptom_feedback_session_id,
-- idx_qualitative_feedback_sess_id). No new index needed there.

-- DashboardController "pending reports" / "active cases" queue queries
-- (CaseRepository.getPendingReportsForQueue / getActiveCasesForQueue) filter
-- on healing_case(healer_id, status) together — V0 only indexes healer_id
-- and clinic_id separately, forcing a filter step on status after the
-- healer_id index scan on every dashboard load.
CREATE INDEX idx_healing_case_healer_status
    ON healing_case (healer_id, status);
