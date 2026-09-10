package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.pranicdoc.cph.common.model.BaseEntity;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.time.LocalDate;

/**
 * CaseSymptom — evolving symptoms for a case.
 * Can be added/resolved mid-case (not fixed at creation).
 */
@Entity
@Table(name = "case_symptom")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class CaseSymptom extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_case_id", nullable = false)
    @JsonBackReference
    private HealingCase healingCase;

    @Column(name = "symptom_name", nullable = false)
    private String symptomName;

    @Column(name = "description")
    private String description;

    @Column(name = "severity")
    private Integer severity; // Optional initial severity (0-10)

    @Column(name = "added_date")
    private LocalDate addedDate;

    @Column(name = "resolved_date")
    private LocalDate resolvedDate;

    @Column(name = "resolved")
    @Builder.Default
    private boolean resolved = false;
}
