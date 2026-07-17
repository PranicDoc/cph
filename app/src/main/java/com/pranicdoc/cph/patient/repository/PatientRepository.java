package com.pranicdoc.cph.patient.repository;

import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.patient.dto.PatientCaseResponse;
import com.pranicdoc.cph.patient.dto.PatientResponse;
import com.pranicdoc.cph.patient.model.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    /**
     * Search patients by name (first or last, case-insensitive).
     * Used for autocomplete on the frontend.
     */
    @Query("SELECT p FROM Patient p WHERE " +
           "LOWER(p.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Patient> searchByName(@Param("query") String query, Pageable pageable);

    @Query("""
        SELECT new com.pranicdoc.cph.patient.dto.PatientCaseResponse(
            p.id,
            p.firstName,
            p.lastName,
            p.dob,
            p.gender,
            p.maritalStatus,
            p.email,
            p.contact,
            p.occupation,
            p.addressLine1,
            p.addressLine2,
            p.city,
            p.state,
            p.zip,
            p.createdAt,
            p.updatedAt,
            hc.status,
            hc.id,
            hc.createdAt
        )
        FROM Patient p
        LEFT JOIN HealingCase hc
            ON hc.patientId = p.id
            AND hc.createdAt = (
                SELECT MAX(hc2.createdAt)
                FROM HealingCase hc2
                WHERE hc2.patientId = p.id
                AND hc2.isDeleted = false
            )
        WHERE p.healerId = :healerId
        AND p.isDeleted = false
        AND hc.id IS NOT NULL
        AND hc.status IN :statuses
        AND hc.createdAt >= :caseFrom
        AND hc.createdAt < :caseTo
    """)
    List<PatientCaseResponse> fetchPatientsWithLatestCaseStatus(
            @Param("healerId") UUID healerId,
            @Param("statuses") Collection<CaseStatus> statuses,
            @Param("caseFrom") Instant caseFrom,
            @Param("caseTo") Instant caseTo,
            Sort sort);

    @Query(""" 
        SELECT COUNT(p) FROM Patient p WHERE p.healerId = :healerId
    """)
    long fetchAllActivePatientsCount(@Param("healerId") UUID healerId);

    @Query(nativeQuery = true, value = """
        SELECT DATE(p.created_at) AS date,
               COUNT(*) AS count
        FROM patient p
        WHERE p.healer_id = :healerId
          AND DATE(p.created_at) >= CAST(:startDate AS DATE)
          AND DATE(p.created_at) <= CAST(:endDate AS DATE)
          AND p.is_deleted = false
        GROUP BY DATE(p.created_at)
        ORDER BY date ASC
    """)
    List<PatientGrowthPoint> getPatientGrowth(
            @Param("healerId") UUID healerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    interface PatientGrowthPoint {
        java.time.LocalDate getDate();
        Long getCount();
    }

}
