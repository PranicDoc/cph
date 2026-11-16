package com.pranicdoc.cph.healing.repository;

import java.util.Collection;
import org.springframework.data.jpa.repository.Modifying;
import com.pranicdoc.cph.common.enums.JobStatus;
import com.pranicdoc.cph.healing.model.PdfJobEntity;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PdfJobRepository extends JpaRepository<PdfJobEntity, UUID> {
    Optional<PdfJobEntity> findTopByHealingCaseIdOrderByCreatedAtDesc(UUID healingCaseId);
    Optional<PdfJobEntity> findFirstByJobIdOrderByCreatedAtDesc(String jobId);
    List<PdfJobEntity> findByHealingCaseIdIn(java.util.Collection<UUID> healingCaseIds);

    Window<PdfJobEntity> findByStatusAndHealingCaseIdIn(
            JobStatus status,
            List<UUID> healingCaseIds,
            ScrollPosition position,
            Limit limit,
            Sort sort
    );
    @Query("""
        SELECT COUNT(p)
        FROM PdfJobEntity p
        JOIN HealingCase hc
          ON p.healingCaseId = hc.id
        WHERE hc.healerId = :healerId
          AND p.createdAt >= :startOfMonth
          AND p.createdAt < :startOfNextMonth
    """)
    long countPdfsGeneratedThisMonth(
            @Param("healerId") UUID healerId,
            @Param("startOfMonth") Instant startOfMonth,
            @Param("startOfNextMonth") Instant startOfNextMonth
    );

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Query(value = "SELECT file_url FROM pdf_job WHERE file_url IS NOT NULL AND healing_case_id IN (:ids)", nativeQuery = true)
    List<String> findFileUrlsByCaseIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM pdf_job_aud WHERE id IN (SELECT id FROM pdf_job WHERE healing_case_id IN (:ids))", nativeQuery = true)
    int eraseAuditByCaseIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM pdf_job WHERE healing_case_id IN (:ids)", nativeQuery = true)
    int eraseByCaseIds(@Param("ids") Collection<UUID> ids);
}
