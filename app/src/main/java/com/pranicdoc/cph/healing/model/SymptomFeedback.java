package com.pranicdoc.cph.healing.model;

import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * SymptomFeedback — patient's symptom rating per session (scale 0-10).
 * UNIQUE(session_id, case_symptom_id) — one rating per symptom per session.
 */
@Entity
@Table(name = "symptom_feedback",
        uniqueConstraints = @UniqueConstraint(columnNames = {"healing_session_id", "case_symptom_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class SymptomFeedback extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_session_id", nullable = false)
    private HealingSession healingSession;

    @Column(name = "case_symptom_id", nullable = false)
    private UUID caseSymptomId; // FK to CaseSymptom

    @Column(name = "rating", nullable = false)
    private Integer rating; // 0-10 scale

    @Column(name = "notes")
    private String notes;
}
