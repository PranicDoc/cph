package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.analytics.dto.DashboardTopSymptom;
import com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.healing.dto.SymptomFeedbackDTO;
import com.pranicdoc.cph.healing.model.HealingCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface CaseRepository extends JpaRepository<HealingCase, UUID> {

    Page<HealingCase> findByPatientId(UUID patientId, Pageable pageable);

    Page<HealingCase> findByHealerId(UUID healerId, Pageable pageable);

    boolean existsByPatientIdAndClinicIdAndStatus(UUID uuid, UUID currentClinicId, CaseStatus caseStatus);

    @Query("""
        SELECT hc.id
        FROM HealingCase hc
        WHERE hc.healerId = :healerId
    """)
    List<UUID> findAllHealingCaseIdsByHealerId(@Param("healerId") UUID healerId);

    @Query("""
        SELECT new com.pranicdoc.cph.healing.dto.SymptomFeedbackDTO(
            s.id,
            s.symptomName
        )
        FROM CaseSymptom s
        WHERE s.healingCase.id = :caseId
    """)
    Page<SymptomFeedbackDTO> findAllSymptomsByCaseId(
            @Param("caseId") UUID caseId,
            Pageable pageable
    );

    @Query("""
        SELECT new com.pranicdoc.cph.analytics.dto.DashboardTopSymptom(cs.symptomName, COUNT(cs))
        FROM CaseSymptom cs
        JOIN cs.healingCase hc
        WHERE hc.healerId = :healerId
          AND (cs.addedDate IS NULL OR (cs.addedDate >= :from AND cs.addedDate <= :to))
        GROUP BY cs.symptomName
        ORDER BY COUNT(cs) DESC
    """)
    List<DashboardTopSymptom> getTopSymptoms(
            @Param("healerId") UUID healerId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    @Query(nativeQuery = true, value = """
        SELECT hc.id                    AS id,
               p.first_name             AS patientFirstName,
               p.last_name              AS patientLastName,
               MAX(hs.session_date)     AS lastSessionDate
        FROM healing_case hc
        JOIN patient p ON p.id = hc.patient_id
        LEFT JOIN healing_session hs
               ON hs.healing_case_id = hc.id AND hs.is_deleted = false
        WHERE hc.healer_id = CAST(:healerId AS uuid)
          AND hc.status = 'ACTIVE'
          AND hc.is_deleted = false
          AND p.is_deleted  = false
          AND NOT EXISTS (
                SELECT 1 FROM healing_session dhs
                WHERE dhs.healing_case_id = hc.id
                  AND dhs.status = 'DRAFT'
                  AND dhs.is_deleted = false
          )
        GROUP BY hc.id, p.first_name, p.last_name
        ORDER BY MAX(hs.session_date) DESC NULLS LAST
    """)
    List<ActiveCasePoint> getActiveCasesForQueue(@Param("healerId") UUID healerId);

    interface ActiveCasePoint {
        UUID getId();
        String getPatientFirstName();
        String getPatientLastName();
        LocalDate getLastSessionDate();
    }

    @Query(nativeQuery = true, value = """
        SELECT hc.id           AS caseId,
               p.first_name    AS patientFirstName,
               p.last_name     AS patientLastName,
               cc.closure_date AS closureDate
        FROM healing_case hc
        JOIN patient p ON p.id = hc.patient_id
        LEFT JOIN case_closure cc ON cc.healing_case_id = hc.id
        WHERE hc.healer_id = CAST(:healerId AS uuid)
          AND hc.status = 'CLOSED'
          AND hc.is_deleted = false
          AND p.is_deleted  = false
          AND NOT EXISTS (
                SELECT 1 FROM pdf_job pj
                WHERE pj.healing_case_id = hc.id
                  AND pj.status = 'COMPLETED'
          )
        ORDER BY cc.closure_date DESC NULLS LAST
    """)
    List<PendingReportPoint> getPendingReportsForQueue(@Param("healerId") UUID healerId);

    interface PendingReportPoint {
        UUID getCaseId();
        String getPatientFirstName();
        String getPatientLastName();
        LocalDate getClosureDate();
    }
}
