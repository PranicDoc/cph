package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.common.enums.JobStatus;
import com.pranicdoc.cph.healing.model.PdfJobEntity;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PdfJobRepository extends JpaRepository<PdfJobEntity, UUID> {
    Optional<PdfJobEntity> findTopByHealingCaseIdOrderByCreatedAtDesc(UUID healingCaseId);
    Window<PdfJobEntity> findByStatusAndHealingCaseIdIn(
            JobStatus status,
            List<UUID> healingCaseIds,
            ScrollPosition position,
            Limit limit,
            Sort sort
    );
}
