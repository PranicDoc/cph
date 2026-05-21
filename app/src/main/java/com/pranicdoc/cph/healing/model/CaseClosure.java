package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;

/**
 * CaseClosure — exists only when the case is closed.
 * Contains summary and additionalComments of the healing case.
 */
@Entity
@Table(name = "case_closure")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class CaseClosure extends TenantEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_case_id", nullable = false, unique = true)
    @JsonBackReference
    private HealingCase healingCase;

    @Column(name = "closure_date")
    private LocalDate closureDate;

    @Column(name = "additional_comments", columnDefinition = "TEXT")
    private String additionalComments;

    @Column(name = "healing_count")
    private Integer healingCount;

    @Column(name = "is_healing_continued")
    private Boolean isHealingContinued;

    @Column(name = "last_feedback_date")
    private LocalDate lastFeedbackDate;

    @Column(name = "healer_signature")
    private String healerSignature;

    @Column(name = "closed_by")
    private java.util.UUID closedBy;
}
