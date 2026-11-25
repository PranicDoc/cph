package com.pranicdoc.cph.patient.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.pranicdoc.cph.common.encryption.EncryptedStringConverter;
import com.pranicdoc.cph.common.model.TenantEntity;
import com.pranicdoc.cph.patient.dto.MedicalHistoryRequest;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Patient medical history — living master record, updated anytime.
 * 1:1 with Patient. Separate from CaseMedicalSnapshot (frozen at case creation).
 */
@Entity
@Table(name = "patient_medical_history")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class PatientMedicalHistory extends TenantEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false, unique = true)
    @JsonBackReference
    private Patient patient;

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
    private String cancerLocation; // 🔒 Encrypted

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "cancer_details")
    private String cancerDetails; // 🔒 Encrypted

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes; // 🔒 Encrypted

    /** Optional. BMI is derived from these two, never stored. */
    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 5, scale = 1)
    private BigDecimal weightKg;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "past_history", columnDefinition = "TEXT")
    private String pastHistory; // 🔒 Encrypted

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "surgical_history", columnDefinition = "TEXT")
    private String surgicalHistory; // 🔒 Encrypted

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "family_history", columnDefinition = "TEXT")
    private String familyHistory; // 🔒 Encrypted

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "psychosocial_history", columnDefinition = "TEXT")
    private String psychosocialHistory; // 🔒 Encrypted

    /** Copy every editable field from the request; the one place create and update share. */
    public void apply(MedicalHistoryRequest r) {
        generalHealth = r.generalHealth();
        diet = r.diet();
        exercise = r.exercise();
        exerciseFrequency = r.exerciseFrequency();
        hypertension = r.hypertension();
        giIssues = r.giIssues();
        stressDepressionAnxiety = r.stressDepressionAnxiety();
        pregnant = r.pregnant();
        cancer = r.cancer();
        cancerLocation = r.cancerLocation();
        cancerDetails = r.cancerDetails();
        additionalNotes = r.additionalNotes();
        heightCm = r.heightCm();
        weightKg = r.weightKg();
        pastHistory = r.pastHistory();
        surgicalHistory = r.surgicalHistory();
        familyHistory = r.familyHistory();
        psychosocialHistory = r.psychosocialHistory();
    }
}
