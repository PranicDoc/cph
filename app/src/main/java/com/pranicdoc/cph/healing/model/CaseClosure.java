package com.pranicdoc.cph.healing.model;

import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;

/**
 * CaseClosure — exists only when the case is closed.
 * Contains summary and outcome of the healing case.
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
    private HealingCase healingCase;

    @Column(name = "closure_date")
    private LocalDate closureDate;

    @Column(name = "outcome", columnDefinition = "TEXT")
    private String outcome;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "closed_by")
    private java.util.UUID closedBy;
}
