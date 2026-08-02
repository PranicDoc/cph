package com.pranicdoc.cph.consent.dto;

import com.pranicdoc.cph.consent.model.ConsentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Caller-facing view of a Consent row. Deliberately omits the request-level
 * forensic/audit metadata captured at acceptance time — that data is not
 * caller-facing (recoverable via the AuditLog.entityId -> Consent join, see
 * AUDIT-01).
 */
public record ConsentResponse(
        UUID id,
        UUID subjectId,
        ConsentType consentType,
        int policyVersion,
        Instant acceptedAt,
        Instant withdrawnAt,
        String method
) {}
