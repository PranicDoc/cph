package com.pranicdoc.cph.patient.repository;

import org.springframework.data.jpa.repository.Modifying;
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
            hc.createdAt,
            p.patientCode
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

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---
    // Ids are selected as text and converted to UUID: a native UUID column comes back as byte[] on H2.

    @Query(value = "SELECT CAST(id AS VARCHAR(36)) FROM patient WHERE healer_id = :healerId", nativeQuery = true)
    List<UUID> findIdsByHealerId(@Param("healerId") UUID healerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM patient_aud WHERE id IN (:ids)", nativeQuery = true)
    int eraseAuditByIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM patient WHERE id IN (:ids)", nativeQuery = true)
    int eraseByIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE patient SET is_deleted = true, deleted_at = now(), updated_at = now() WHERE id = :id AND is_deleted = false", nativeQuery = true)
    int trash(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE patient SET is_deleted = false, deleted_at = NULL, updated_at = now() WHERE id = :id AND is_deleted = true AND deleted_at IS NOT NULL", nativeQuery = true)
    int restore(@Param("id") UUID id);

    @Query(value = "SELECT CAST(id AS VARCHAR(36)) FROM patient WHERE is_deleted AND deleted_at IS NOT NULL AND deleted_at < :cutoff", nativeQuery = true)
    List<UUID> findIdsTrashedBefore(@Param("cutoff") java.sql.Timestamp cutoff);

    /** Trashed patients with their healer name and the count of cases that went to the trash with them. */
    @Query(value = """
            SELECT CAST(p.id AS VARCHAR(36)) AS "id", p.patient_code AS "patientCode", p.first_name AS "firstName", p.last_name AS "lastName",
                   CAST(p.healer_id AS VARCHAR(36)) AS "healerId", h.full_name AS "healerName", p.deleted_at AS "deletedAt",
                   (SELECT count(*) FROM healing_case c WHERE c.patient_id = p.id AND c.deleted_with_patient) AS "cases"
              FROM patient p LEFT JOIN healer h ON h.id = p.healer_id
             WHERE p.is_deleted AND p.deleted_at IS NOT NULL
             ORDER BY p.deleted_at DESC
            """, nativeQuery = true)
    List<TrashedPatientRow> findTrashed();

    /** Native projection; {@code deletedAt} and {@code cases} come back as driver types, converted by the caller. */
    interface TrashedPatientRow {
        UUID getId();
        String getPatientCode();
        String getFirstName();
        String getLastName();
        UUID getHealerId();
        String getHealerName();
        Object getDeletedAt();
        Number getCases();
    }
}
