package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.pranicdoc.cph.common.encryption.EncryptedStringConverter;
import com.pranicdoc.cph.common.model.BaseEntity;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import com.pranicdoc.cph.patient.model.PatientMedicalHistory;
import org.hibernate.envers.Audited;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The case's copy of the patient's medical history; the case PDF and Case Summary read this.
 * Created with the case and refreshed on every medical-history edit
 * ({@code PatientService.updateMedicalHistory}), so it shows the current values. Written only via {@link #upsert}. Earlier values remain
 * in the Envers audit table. A new medical-history field must be added here and in copyFrom
 * (PatientServiceTest#snapshotCopiesEveryMedicalHistoryField fails otherwise).
 */
@Entity
@Table(name = "case_medical_snapshot")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class CaseMedicalSnapshot extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_case_id", nullable = false, unique = true)
    @JsonBackReference
    private HealingCase healingCase;

    @Column(name = "general_health")
    private String generalHealth;

    @Column(name = "diet")
    private String diet;

    @Column(name = "exercise")
    private String exercise;

    @Column(name = "exercise_frequency")
    private String exerciseFrequency;

    @Column(name = "hypertension")
    private Boolean hypertension;

    @Column(name = "gi_issues")
    private Boolean giIssues;

    @Column(name = "stress_depression_anxiety")
    private Boolean stressDepressionAnxiety;

    @Column(name = "pregnant")
    private Boolean pregnant;

    @Column(name = "cancer")
    private Boolean cancer;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "cancer_location")
    private String cancerLocation; // 🔒

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "cancer_details")
    private String cancerDetails; // 🔒

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes; // 🔒

    /**
     * Upsert: the ONE way to write a case's medical snapshot (case creation and every medical-history edit).
     * Reuses the case's snapshot; otherwise its soft-deleted row ({@code findIncludingDeleted}, only consulted
     * for a saved case); otherwise a new one. Restores it, copies the current history and attaches it.
     */
    public static CaseMedicalSnapshot upsert(HealingCase healingCase, PatientMedicalHistory history,
                                             Function<UUID, Optional<CaseMedicalSnapshot>> findIncludingDeleted) {
        CaseMedicalSnapshot snapshot = healingCase.getMedicalSnapshot();
        if (snapshot == null && healingCase.getId() != null) {
            snapshot = findIncludingDeleted.apply(healingCase.getId()).orElse(null);
        }
        if (snapshot == null) {
            snapshot = new CaseMedicalSnapshot();
        }
        snapshot.setHealingCase(healingCase);
        snapshot.setDeleted(false);
        snapshot.copyFrom(history);
        healingCase.setMedicalSnapshot(snapshot);
        return snapshot;
    }

    /** Overwrites every medical field with the patient's current history (nulls included, so cleared values clear). */
    private void copyFrom(PatientMedicalHistory history) {
        this.generalHealth = history.getGeneralHealth();
        this.diet = history.getDiet();
        this.exercise = history.getExercise();
        this.exerciseFrequency = history.getExerciseFrequency();
        this.hypertension = history.getHypertension();
        this.giIssues = history.getGiIssues();
        this.stressDepressionAnxiety = history.getStressDepressionAnxiety();
        this.pregnant = history.getPregnant();
        this.cancer = history.getCancer();
        this.cancerLocation = history.getCancerLocation();
        this.cancerDetails = history.getCancerDetails();
        this.additionalNotes = history.getAdditionalNotes();
    }
}
