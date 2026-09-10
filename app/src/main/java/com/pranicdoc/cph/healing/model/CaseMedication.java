package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.pranicdoc.cph.common.model.BaseEntity;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

/**
 * CaseMedication — medications the patient is taking during the case.
 */
@Entity
@Table(name = "case_medication")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class CaseMedication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_case_id", nullable = false)
    @JsonBackReference
    private HealingCase healingCase;

    @Column(name = "medication_name", nullable = false)
    private String medicationName;

    @Column(name = "dosage")
    private String dosage;

    @Column(name = "morning_dose")
    private Boolean morningDose;

    @Column(name = "afternoon_dose")
    private Boolean afternoonDose;

    @Column(name = "night_dose")
    private Boolean nightDose;

    @Column(name = "notes")
    private String notes;
}
