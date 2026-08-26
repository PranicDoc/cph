package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.util.List;

@Entity
@Table(name = "treatment_step")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@Audited
public class TreatmentStep extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_details_id")
    @JsonBackReference
    private HealingDetails healingDetails;

    @Column(name = "tag", nullable = false)
    private String tag;

    @Column(name = "sweep_type")
    private String sweepType;

    @Column(name = "notes")
    private String notes;

    @Column(name = "cycles")
    private int cycles;

    @Column(name = "step", nullable = false)
    private int step;

    // D-25/INFRA-01: was FetchType.EAGER — fired one extra SELECT per
    // TreatmentStep loaded (N+1 on the PDF-generation snapshot path).
    // hibernate.default_batch_fetch_size (application.yaml) batches this
    // LAZY collection's load across all TreatmentSteps in the persistence
    // context into one "WHERE id IN (...)" query.
    @ElementCollection(fetch = FetchType.LAZY)
    private List<String> targets;

    @Column(name = "color")
    private String color;
}
