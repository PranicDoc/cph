package com.pranicdoc.cph.consent.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.UUID;

/**
 * Consent — versioned consent capture record (CONSENT-01). Never a boolean
 * accepted-flag; a new row (or acceptedAt/withdrawnAt state change) is written
 * for every acceptance/withdrawal event.
 *
 * NOTE — acceptedBy is a distinct, explicit domain column required verbatim by
 * CONSENT-01. It is NOT the inherited BaseEntity.createdBy (Spring Data
 * @CreatedBy auditing). In today's self-service flow acceptedBy == subjectId
 * (both the current healer id), but the generic subjectType/subjectId design
 * deliberately supports future non-self subjects where acceptedBy and
 * subjectId diverge.
 *
 * NAMING-COLLISION RESOLUTION — BaseEntity already maps a `version` BIGINT
 * column for @Version optimistic locking. The domain policy-version-number
 * field is named `policyVersion` (column policy_version) to avoid colliding
 * with it.
 */
@Entity
@Table(name = "consent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class Consent extends BaseEntity {

    @Column(name = "subject_type", nullable = false, length = 30)
    private String subjectType; // e.g. "HEALER" — generic per CONSENT-01, not FK-bound

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId; // in practice, healer.id

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 40)
    private ConsentType consentType;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion; // D-08: simple integer sequence per consentType

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by")
    private UUID acceptedBy; // explicit domain column — the practitioner who performed the acceptance

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "method", length = 30)
    private String method; // e.g. "CLICKWRAP" — captured server-side (CONSENT-05)

    @Column(name = "ip_address", length = 255)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "language", length = 10)
    private String language;
}
