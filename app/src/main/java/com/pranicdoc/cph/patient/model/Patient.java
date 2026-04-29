package com.pranicdoc.cph.patient.model;

import com.pranicdoc.cph.common.encryption.EncryptedStringConverter;
import com.pranicdoc.cph.common.enums.Gender;
import com.pranicdoc.cph.common.enums.MaritalStatus;
import com.pranicdoc.cph.common.model.TenantEntity;
import com.pranicdoc.cph.common.tenant.TenantContext;
import com.pranicdoc.cph.patient.dto.MedicalHistoryRequest;
import com.pranicdoc.cph.patient.dto.PatientCreateRequest;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Patient entity — core patient demographics.
 * Fields marked with @Convert are AES-256 encrypted at rest.
 */
@Entity
@Table(name = "patient")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Audited
public class Patient extends TenantEntity {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "dob")
    private LocalDate dob; // Age derived dynamically, not stored

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 10)
    private MaritalStatus maritalStatus;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email")
    private String email; // 🔒 Encrypted

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact_whatsapp")
    private String contact; // 🔒 Encrypted

    @Column(name = "occupation")
    private String occupation;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address_line1")
    private String addressLine1; // 🔒 Encrypted

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address_line2")
    private String addressLine2; // 🔒 Encrypted

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "zip", length = 10)
    private String zip;

    // 1:1 bidirectional with PatientMedicalHistory (living master, updated anytime)
    @OneToOne(mappedBy = "patient", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PatientMedicalHistory medicalHistory;

    public static Patient create(PatientCreateRequest req) {
        Patient p = new Patient();
        populateIds(p);
        // business fields
        p.setFirstName(req.firstName());
        p.setLastName(req.lastName());
        p.setDob(req.dob());
        p.setGender(req.gender());
        p.setMaritalStatus(req.maritalStatus());
        p.setEmail(req.email());
        p.setContact(req.contact());
        p.setOccupation(req.occupation());
        p.setAddressLine1(req.addressLine1());
        p.setAddressLine2(req.addressLine2());
        p.setCity(req.city());
        p.setState(req.state());
        p.setZip(req.zip());

        PatientMedicalHistory history = getPatientMedicalHistory(req.medicalHistory(), p);
        p.setMedicalHistory(history);

        return p;
    }

    private static PatientMedicalHistory getPatientMedicalHistory(MedicalHistoryRequest historyReq, Patient p) {
        PatientMedicalHistory history = new PatientMedicalHistory();
        populateIds(history);
        history.setGeneralHealth(historyReq.generalHealth());
        history.setDiet(historyReq.diet());
        history.setExercise(historyReq.exercise());
        history.setExerciseFrequency(historyReq.exerciseFrequency());
        history.setHypertension(historyReq.hypertension());
        history.setGiIssues(historyReq.giIssues());
        history.setStressDepressionAnxiety(historyReq.stressDepressionAnxiety());
        history.setPregnant(historyReq.pregnant());
        history.setCancer(historyReq.cancer());
        history.setCancerLocation(historyReq.cancerLocation());
        history.setCancerDetails(historyReq.cancerDetails());
        history.setAdditionalNotes(historyReq.additionalNotes());
        history.setPatient(p);
        return history;
    }
}
