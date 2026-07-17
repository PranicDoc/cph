package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.pranicdoc.cph.common.encryption.EncryptedStringConverter;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HealingCase — aggregate root for the CPH form.
 * One case per ailment per patient.
 */
@Entity
@Table(name = "healing_case")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Audited
public class HealingCase extends TenantEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    // healerId is inherited from TenantEntity (post-26-03: maps the same healer_id column)

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "medical_condition", columnDefinition = "TEXT")
    private String medicalCondition; // 🔒

    @JsonManagedReference
    @OneToOne(mappedBy = "healingCase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private HealingDetails healingDetails;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "lab_findings", columnDefinition = "TEXT")
    private String labFindings; // 🔒

    @Column(name = "complementary_treatments")
    private Boolean complementaryTreatments;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "complementary_details", columnDefinition = "TEXT")
    private String complementaryDetails; // 🔒

    @Column(name = "infectious_illness")
    private Boolean infectiousIllness;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "infectious_details", columnDefinition = "TEXT")
    private String infectiousDetails; // 🔒

    @Column(name = "basic_book_pg")
    private String basicBookPg;

    @Column(name = "advanced_book_pg")
    private String advancedBookPg;

    @Column(name = "psychotherapy_pg")
    private String psychotherapyPg;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private CaseStatus status = CaseStatus.DRAFT;

    // --- Relationships ---

    @OneToOne(mappedBy = "healingCase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private CaseMedicalSnapshot medicalSnapshot;

    @OneToOne(mappedBy = "healingCase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Declaration declaration;

    @OneToMany(mappedBy = "healingCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference
    private List<CaseSymptom> symptoms = new ArrayList<>();

    @OneToMany(mappedBy = "healingCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference
    private List<CaseMedication> medications = new ArrayList<>();

    @OneToOne(mappedBy = "healingCase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private CaseClosure closure;

    @OneToMany(mappedBy = "healingCase", cascade = CascadeType.ALL)
    @Builder.Default
    @JsonManagedReference
    private List<HealingSession> sessions = new ArrayList<>();

    public static HealingCase create(UUID patientId, UUID healerId, Declaration declaration) {
        if (declaration == null) {
            throw new IllegalArgumentException("Declaration is required");
        }

        HealingCase healingCase = new HealingCase();
        populateIds(healingCase);

        healingCase.setPatientId(patientId);
        healingCase.setHealerId(healerId);
        healingCase.setStatus(CaseStatus.DRAFT);

        // bidirectional link
        healingCase.setDeclaration(declaration);
        declaration.setHealingCase(healingCase);

        return healingCase;
    }
}
