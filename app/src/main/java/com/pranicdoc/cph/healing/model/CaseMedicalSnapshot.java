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

/**
 * The case's copy of the patient's medical history; the case PDF and Case Summary read this.
 * Created with the case and refreshed on every medical-history edit
 * ({@code PatientService.updateMedicalHistory}), so it shows the current values. Earlier values remain
 * in the Envers audit table. A new medical-history field must be added here and in {@link #copyFrom}
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

    /** A new snapshot of {@code history} for {@code healingCase}. */
    public static CaseMedicalSnapshot of(HealingCase healingCase, PatientMedicalHistory history) {
        CaseMedicalSnapshot snapshot = new CaseMedicalSnapshot();
        snapshot.setHealingCase(healingCase);
        snapshot.copyFrom(history);
        return snapshot;
    }

    /** Overwrites every medical field with the patient's current history (nulls included, so cleared values clear). */
    public void copyFrom(PatientMedicalHistory history) {
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
