package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.analytics.dto.DashboardSessionTrend;
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
}
