package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.analytics.dto.DashboardSessionTrend;
import com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession;
import com.pranicdoc.cph.healing.model.HealingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<HealingSession, UUID> {

    List<HealingSession> findByHealingCaseIdOrderBySessionNumberAsc(UUID healingCaseId);

    Optional<HealingSession> findByClientRequestId(UUID clientRequestId);

    Optional<HealingSession> findFirstByHealingCase_IdOrderBySessionDateDesc(UUID caseId);

    @Query("""
        SELECT new com.pranicdoc.cph.analytics.dto.DashboardSessionTrend(s.sessionDate, COUNT(s))
        FROM HealingSession s
        JOIN s.healingCase hc
        WHERE hc.healerId = :healerId
          AND s.sessionDate >= :startDate
          AND s.sessionDate <= :endDate
        GROUP BY s.sessionDate
        ORDER BY s.sessionDate ASC
    """)
    List<DashboardSessionTrend> getSessionTrends(
            @Param("healerId") UUID healerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT COUNT(s)
        FROM HealingSession s
        JOIN s.healingCase hc
        WHERE hc.healerId = :healerId
          AND s.createdAt >= :startOfMonth
          AND s.createdAt < :startOfNextMonth
    """)
    long getAllSessionsCountThisMonth(@Param("healerId") UUID healerId,
                                      @Param("startOfMonth") Instant startOfMonth,
                                      @Param("startOfNextMonth") Instant startOfNextMonth);

    @Query("""
        SELECT new com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession(
            hs.id, p.firstName, p.lastName, hs.sessionDate, hs.startTime, 'DRAFT_SESSION'
        )
        FROM HealingSession hs
        JOIN hs.healingCase hc
        JOIN com.pranicdoc.cph.patient.model.Patient p ON p.id = hc.patientId
        WHERE hc.healerId = :healerId
          AND hs.status = com.pranicdoc.cph.common.enums.SessionStatus.DRAFT
        ORDER BY hs.sessionDate DESC NULLS LAST
    """)
    List<DashboardUpcomingSession> getDraftSessions(@Param("healerId") UUID healerId);

    @Query("""
        SELECT DISTINCT cr.chakraName
        FROM HealingSession hs
        JOIN hs.scans cs
        JOIN cs.readings cr
        WHERE hs.healingCase.id = :caseId
    """)
    Set<String> findAllScannedChakras(@Param("caseId") UUID caseId);
}
