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
     * (also PATIENT-token — D-19); {@code getSymptomsForFeedback} stays ungated
     * (asserted in plan 42-10).
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
