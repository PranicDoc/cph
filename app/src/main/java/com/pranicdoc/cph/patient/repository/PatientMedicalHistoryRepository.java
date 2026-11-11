package com.pranicdoc.cph.patient.repository;

import com.pranicdoc.cph.patient.model.PatientMedicalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientMedicalHistoryRepository extends JpaRepository<PatientMedicalHistory, UUID> {

    /**
     * The patient's history row even if soft-deleted (native SQL is not filtered by @SQLRestriction), so an
     * upsert restores it instead of inserting a second row and hitting uq_patient_medical_history_pid.
     */
    @Query(value = "SELECT * FROM patient_medical_history WHERE patient_id = :patientId", nativeQuery = true)
    Optional<PatientMedicalHistory> findIncludingDeletedByPatientId(@Param("patientId") UUID patientId);
}
