package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.analytics.dto.DashboardPendingReport;
import com.pranicdoc.cph.analytics.dto.DashboardSessionTrend;
import com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession;
import com.pranicdoc.cph.healing.model.HealingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<HealingSession, UUID> {

    List<HealingSession> findByHealingCaseIdOrderBySessionNumberAsc(UUID healingCaseId);

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
            @Param("healerId") String healerId,
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
    long getAllSessionsCountThisMonth(String healerId, LocalDateTime startOfMonth, LocalDateTime startOfNextMonth);

    @Query("""
        SELECT new com.pranicdoc.cph.analytics.dto.DashboardUpcomingSession(
            hs.id, p.firstName, p.lastName, hs.sessionDate, hs.startTime, hs.status
        )
        FROM HealingSession hs
        JOIN hs.healingCase hc
        JOIN com.pranicdoc.cph.patient.model.Patient p ON p.id = hc.patientId
        WHERE hc.healerId = :healerId
          AND hs.sessionDate >= :today
        ORDER BY hs.sessionDate ASC, hs.startTime ASC
    """)
    List<DashboardUpcomingSession> getUpcomingSessions(
            @Param("healerId") UUID healerId,
            @Param("today") LocalDate today);

    @Query("""
        SELECT new com.pranicdoc.cph.analytics.dto.DashboardPendingReport(
            hs.id, p.firstName, p.lastName, hs.sessionDate
        )
        FROM HealingSession hs
        JOIN hs.healingCase hc
        JOIN com.pranicdoc.cph.patient.model.Patient p ON p.id = hc.patientId
        WHERE hc.healerId = :healerId
          AND hs.status = com.pranicdoc.cph.common.enums.SessionStatus.COMPLETED
          AND NOT EXISTS (
              SELECT pj FROM com.pranicdoc.cph.healing.model.PdfJobEntity pj
              WHERE pj.healingCaseId = hc.id
                AND pj.status = com.pranicdoc.cph.common.enums.JobStatus.COMPLETED
          )
        ORDER BY hs.sessionDate DESC
    """)
    List<DashboardPendingReport> getPendingReports(
            @Param("healerId") UUID healerId);
}
