package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.pranicdoc.cph.common.encryption.EncryptedStringConverter;
import com.pranicdoc.cph.common.model.BaseEntity;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

/**
 * Frozen medical snapshot captured at case creation time.
 * Distinct from PatientMedicalHistory (which is a living record).
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
}
