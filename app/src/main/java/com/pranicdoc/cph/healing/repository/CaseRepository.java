package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.analytics.dto.DashboardTopSymptom;
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
}
