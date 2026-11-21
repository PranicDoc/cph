package com.pranicdoc.cph.patient.repository;

import com.pranicdoc.cph.patient.model.PatientMedicalHistory;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientMedicalHistoryRepository extends JpaRepository<PatientMedicalHistory, UUID> {

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM patient_medical_history_aud WHERE id IN (SELECT id FROM patient_medical_history WHERE patient_id IN (:ids))", nativeQuery = true)
    int eraseAuditByPatientIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM patient_medical_history WHERE patient_id IN (:ids)", nativeQuery = true)
    int eraseByPatientIds(@Param("ids") Collection<UUID> ids);
}
