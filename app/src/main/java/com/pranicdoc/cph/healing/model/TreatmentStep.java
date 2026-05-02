package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.util.List;

@Entity
@Table(name = "treatment_step")
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

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> targets;

    @Column(name = "color")
    private String color;
}
