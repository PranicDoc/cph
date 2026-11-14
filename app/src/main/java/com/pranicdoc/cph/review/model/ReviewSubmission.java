package com.pranicdoc.cph.review.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * One review thread for a case at one stage. A healer opens it by submitting the case PDF;
 * later submissions while it is open add revisions to it. A DISMISSED submission is closed:
 * the next submit opens a new one.
 */
@Entity
@Table(name = "review_submission")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ReviewSubmission extends BaseEntity {

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "healer_id", nullable = false, updatable = false)
    private UUID healerId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, updatable = false)
    private ReviewStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReviewStatus status;

    @Builder.Default
    @Column(name = "dismissed_from_approved", nullable = false)
    private boolean dismissedFromApproved = false;

    @Column(name = "dismiss_reason")
    private String dismissReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "submitted_via", nullable = false, updatable = false)
    private SubmittedVia submittedVia;
}
