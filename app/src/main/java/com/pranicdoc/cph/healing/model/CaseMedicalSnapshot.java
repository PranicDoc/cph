package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pranicdoc.common.dto.BmiCalculator;
import com.pranicdoc.cph.common.encryption.EncryptedStringConverter;
import com.pranicdoc.cph.common.model.BaseEntity;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import com.pranicdoc.cph.patient.model.PatientMedicalHistory;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;

/**
 * The case's copy of the patient's medical history. Created with the case, then refreshed on every
 * history edit (see {@link #upsert}) so the PDF and Case Summary show current values.
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

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 5, scale = 1)
    private BigDecimal weightKg;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "past_history", columnDefinition = "TEXT")
    private String pastHistory; // 🔒

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "surgical_history", columnDefinition = "TEXT")
    private String surgicalHistory; // 🔒

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "family_history", columnDefinition = "TEXT")
    private String familyHistory; // 🔒

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "psychosocial_history", columnDefinition = "TEXT")
    private String psychosocialHistory; // 🔒

    /** BMI to one decimal, served with the snapshot; null unless both measurements are present and in range. */
    @JsonProperty("bmi")
    public BigDecimal computeBmi() {
        return BmiCalculator.bmi(heightCm, weightKg).orElse(null);
    }

    /** "Underweight", "Normal", "Overweight" or "Obese"; null when there is no BMI. */
    @JsonProperty("bmiCategory")
    public String computeBmiCategory() {
        BigDecimal bmi = computeBmi();
        return bmi == null ? null : BmiCalculator.category(bmi).label();
    }

    /**
     * Upsert the case's snapshot from the patient's current history (creates it when missing). Called on
     * every medical-history edit, so the PDF shows current values. A new history field goes here too.
     */
    public static void upsert(HealingCase healingCase, PatientMedicalHistory history) {
        CaseMedicalSnapshot s = healingCase.getMedicalSnapshot();
        if (s == null) {
            s = new CaseMedicalSnapshot();
            s.setHealingCase(healingCase);
            healingCase.setMedicalSnapshot(s);
        }
        s.generalHealth = history.getGeneralHealth();
        s.diet = history.getDiet();
        s.exercise = history.getExercise();
        s.exerciseFrequency = history.getExerciseFrequency();
        s.hypertension = history.getHypertension();
        s.giIssues = history.getGiIssues();
        s.stressDepressionAnxiety = history.getStressDepressionAnxiety();
        s.pregnant = history.getPregnant();
        s.cancer = history.getCancer();
        s.cancerLocation = history.getCancerLocation();
        s.cancerDetails = history.getCancerDetails();
        s.additionalNotes = history.getAdditionalNotes();
        s.heightCm = history.getHeightCm();
        s.weightKg = history.getWeightKg();
        s.pastHistory = history.getPastHistory();
        s.surgicalHistory = history.getSurgicalHistory();
        s.familyHistory = history.getFamilyHistory();
        s.psychosocialHistory = history.getPsychosocialHistory();
    }
}
