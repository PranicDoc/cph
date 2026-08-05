package com.pranicdoc.cph.consent.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.UUID;

/**
 * PolicyVersion — registry of policy versions per consentType (D-06). Created
 * ahead of Phase 31's real document content; `content` is a nullable
 * placeholder in this phase (D-07).
 */
@Entity
@Table(name = "policy_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class PolicyVersion extends BaseEntity {

    @Column(name = "consent_type", nullable = false, length = 40)
    private String consentType;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion; // D-08: integer sequence per consentType

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "effective_date", nullable = false)
    private Instant effectiveDate;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content; // D-07: nullable placeholder until Phase 31

    @Column(name = "publisher_id")
    private UUID publisherId; // D-04/D-05 — denormalized "who published this version"; nullable, no FK, not a scoping key (D-06)
}
