package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

/**
 * QualitativeFeedback — free-form interim/final feedback per session.
 * Partial unique: only 1 FINAL per case (enforced at service layer).
 */
@Entity
@Table(name = "qualitative_feedback")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class QualitativeFeedback extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_session_id", nullable = false)
    @JsonBackReference
    private HealingSession healingSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 10)
    private FeedbackType feedbackType; // INTERIM or FINAL

    @Column(name = "feedback_text", columnDefinition = "TEXT")
    private String feedbackText;

    @Column(name = "patient_remarks", columnDefinition = "TEXT")
    private String patientRemarks;
}
