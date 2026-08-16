package com.pranicdoc.cph.consent.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.UUID;

/**
 * ReadingSession — Phase 33's READ-01/READ-02 tracking row. Records that a
 * practitioner opened a given (consentType, policyVersion) document at
 * openedAt and, if they finished, closed it at closedAt with a computed
 * durationSeconds.
 *
 * openedAt, closedAt, and durationSeconds are always server-clock-derived
 * (Instant.now() / Duration.between) and never client-supplied — T-33-01.
 *
 * NAMING-COLLISION RESOLUTION — BaseEntity already maps a `version` BIGINT
 * column for @Version optimistic locking. The domain policy-version-number
 * field is named `policyVersion` (column policy_version) to avoid colliding
 * with it, matching Consent's precedent.
 *
 * Deliberately has NO foreign key or association to Consent — a reading
 * session is not an acceptance. Correlating the two is Phase 34's job
 * (33-RESEARCH.md Pitfall 4).
 */
@Entity
@Table(name = "reading_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ReadingSession extends BaseEntity {

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId; // in practice, healer.id — resolved server-side only

    @Column(name = "consent_type", nullable = false, length = 60)
    private String consentType;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "scroll_completed", nullable = false)
    private boolean scrollCompleted;
}
