package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.List;

@Entity
@Table(name = "healing_details")
@Getter
@Setter
@NoArgsConstructor
@Audited
public class HealingDetails extends BaseEntity {
    @JsonBackReference
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_case_id", nullable = false, unique = true)
    private HealingCase healingCase;

    @Column(name = "protocol_name", nullable = false)
    private String protocolName;

    @Column(name = "book_reference", nullable = false)
    private String bookReference;

    @OneToMany(mappedBy = "healingDetails", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<TreatmentStep> treatmentSteps;
}
