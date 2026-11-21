package com.pranicdoc.cph.review.repository;

import java.util.List;
import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.pranicdoc.cph.review.model.ReviewerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewerAssignmentRepository extends JpaRepository<ReviewerAssignment, UUID> {

    Optional<ReviewerAssignment> findByHealerId(UUID healerId);

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM reviewer_assignment_aud WHERE healer_id = :healerId OR reviewer_id = :healerId OR id IN (SELECT id FROM reviewer_assignment WHERE healer_id = :healerId OR reviewer_id = :healerId)", nativeQuery = true)
    int eraseAuditOfHealer(@Param("healerId") UUID healerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM reviewer_assignment WHERE healer_id = :healerId OR reviewer_id = :healerId", nativeQuery = true)
    int eraseOfHealer(@Param("healerId") UUID healerId);
}
