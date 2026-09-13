package com.pranicdoc.cph.rbac.model;

import java.util.Set;

/**
 * Resource — the fixed resource catalog for RBAC grants. Static Java enum,
 * not a DB-seeded table (superseding V30's original {@code resource} table +
 * {@code resource_permission} allow-list design). {@link #supports(Operation)}
 * is the allow-list check now: {@link Permission}'s constructor calls it at
 * write time, so an invalid (resource, verb) combo is rejected in Java
 * before it ever reaches {@code role_permission} — there is no DB CHECK
 * constraint backing this.
 */
public enum Resource {

    PATIENT(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE,
            Operation.EXPORT
    ),

    SESSION(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE
    ),

    CASE(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE,
            Operation.EXPORT,
            Operation.SHARE
    ),

    PDF(
            Operation.READ,
            Operation.CREATE,
            Operation.DELETE,
            Operation.EXPORT,
            Operation.SHARE
    ),

    CREDIT_ACCOUNTS(
            Operation.READ,
            Operation.CREATE
    ),

    CREDIT_ACCOUNT_TRANSACTIONS(
            Operation.READ,
            Operation.WRITE
    ),

    GLOBAL_CONFIG(
            Operation.READ,
            Operation.UPDATE
    ),

    PACKAGES(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE
    ),

    MASTER_DATA_COLORS(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE
    ),

    MASTER_DATA_PROTOCOLS(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE
    ),

    MASTER_DATA_CHAKRAS(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE
    ),

    HEALER_REG(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE
    ),

    LEGAL_DOCUMENTS(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE,
            Operation.ADMIN
    ),

    ROLES(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE
    ),

    /** Healer account administration (list/activate/deactivate/delete) — distinct from CREDIT_ACCOUNTS' billing reads. */
    ACCOUNTS(
            Operation.READ,
            Operation.UPDATE,
            Operation.DELETE
    ),

    /**
     * Per-session chakra scan submission. Enforced by
     * {@code ScanController.saveScan} (POST {@code /api/v1/sessions/{sessionId}/scans}).
     * CREATE only (D-01/D-03): the sole scan-read path,
     * {@code SessionController.getAllScannedChakras}
     * (GET {@code /api/v1/sessions/{sessionId}/chakras}), returns session-detail and
     * gates as {@code SESSION:READ}, so SCAN has no read endpoint this phase. Do not
     * add UPDATE/DELETE — there is no enforcing endpoint (D-01/D-02).
     *
     * <p><b>WR-02 / IN-03 bundled-write caveat.</b> {@code ScanController.saveScan}
     * is the <em>standalone</em> endpoint only. The cph/ui wizard writes scans by
     * bundling them into {@code POST /api/v1/sessions/case}
     * ({@code SessionController.create}), which is gated {@code SESSION:CREATE}
     * alone; the sub-write runs inside {@code SessionService.create}. Granting or
     * revoking {@code SCAN:CREATE} therefore has <b>no effect on primary UI
     * traffic</b> until the wizard stops bundling. This is acknowledged rather than
     * enforced in {@code SessionService.create} per the 2026-09-02 WR-02 ruling
     * (layer / SC5-dependency-conflict / live-403 reasons — see plan 42-14
     * {@code <objective>}); follow-up logged as {@code D-42-14-01} in
     * {@code .planning/phases/42-app-wide-permission-enforcement-healer-controllers/deferred-items.md}.
     */
    SCAN(Operation.CREATE),

    /**
     * Per-session healer protocol submission. Enforced by
     * {@code ProtocolController.saveProtocol}
     * (POST {@code /api/v1/sessions/{sessionId}/protocol}).
     * Deliberately DISTINCT from {@link #MASTER_DATA_PROTOCOLS}, which is the admin
     * protocol catalog (SC2, D-06): a healer-session protocol grant can never be
     * satisfied by an admin master-data grant. {@code CaseController}'s
     * protocol-understanding pair is {@code CASE:*}, not {@code SESSION_PROTOCOL:*}
     * (D-06). CREATE only — {@code ProtocolController} has one POST and nothing else.
     *
     * <p><b>WR-02 / IN-03 bundled-write caveat.</b> {@code ProtocolController.saveProtocol}
     * is the <em>standalone</em> endpoint only. The cph/ui wizard writes session
     * protocols by bundling them into {@code POST /api/v1/sessions/case}
     * ({@code SessionController.create}), which is gated {@code SESSION:CREATE}
     * alone; the sub-write runs inside {@code SessionService.create}. Granting or
     * revoking {@code SESSION_PROTOCOL:CREATE} therefore has <b>no effect on primary
     * UI traffic</b> until the wizard stops bundling. This is acknowledged rather
     * than enforced in {@code SessionService.create} per the 2026-09-02 WR-02 ruling
     * (layer / SC5-dependency-conflict / live-403 reasons — see plan 42-14
     * {@code <objective>}); follow-up logged as {@code D-42-14-01} in
     * {@code .planning/phases/42-app-wide-permission-enforcement-healer-controllers/deferred-items.md}.
     */
    SESSION_PROTOCOL(Operation.CREATE),

    /**
     * Patient feedback cycle. Enforced SOLELY by
     * {@code CaseController.resendFeedback}
     * (POST {@code /api/v1/cases/{id}/resend-feedback}) — healer-only.
     * CREATE only, per D-19 which amends D-01's {@code {READ, CREATE}}: the only
     * candidate read endpoint, {@code CaseController.getSymptomsForFeedback}, is
     * reachable only via a {@code PATIENT}-role token where
     * {@code @rbacAuthz.hasPermission} returns {@code false} for every real caller, so
     * a {@code FEEDBACK:READ} gate would 403 the patient feedback form. Do NOT add
     * {@code Operation.READ}. The two {@code FeedbackController} POSTs stay ungated
     * (also PATIENT-token — D-19).
     *
     * <p>As of the CR-01/WR-01 ruling (plan 42-13 / D-19a),
     * {@code CaseController.getSymptomsForFeedback} is gated {@code CASE:READ} and
     * the patient caller is served by the ungated
     * {@code CaseController.getSymptomsForFeedbackSelfService} mapping. {@code FEEDBACK}
     * still allow-lists {@code {CREATE}} only and {@code FEEDBACK:READ} must NOT be
     * added — {@code getSymptomsForFeedback}'s gate is {@code CASE:READ}, not
     * {@code FEEDBACK:READ}.
     */
    FEEDBACK(Operation.CREATE);

    private final Set<Operation> supportedOperations;

    Resource(Operation... supportedOperations) {
        this.supportedOperations = Set.of(supportedOperations);
    }

    public Set<Operation> getSupportedOperations() {
        return supportedOperations;
    }

    public boolean supports(Operation operation) {
        return supportedOperations.contains(operation);
    }
}
