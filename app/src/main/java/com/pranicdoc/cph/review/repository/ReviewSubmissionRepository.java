package com.pranicdoc.cph.review.repository;

import org.springframework.data.jpa.repository.Modifying;
import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewSubmissionRepository extends JpaRepository<ReviewSubmission, UUID> {

    Optional<ReviewSubmission> findTopByCaseIdAndStageOrderByCreatedAtDesc(UUID caseId, ReviewStage stage);

    List<ReviewSubmission> findByCaseIdInAndStage(Collection<UUID> caseIds, ReviewStage stage);

    List<ReviewSubmission> findByHealerIdOrderByCreatedAtDesc(UUID healerId);

    List<ReviewSubmission> findByHealerIdAndStageAndStatusIn(UUID healerId, ReviewStage stage,
                                                             Collection<ReviewStatus> statuses);

    List<ReviewSubmission> findByReviewerIdAndStage(UUID reviewerId, ReviewStage stage);

    long countByHealerIdAndStageAndStatus(UUID healerId, ReviewStage stage, ReviewStatus status);

    /** Approved-case counts for a page of healers at once; healers with none are absent from the result. */
    @Query("select s.healerId, count(s) from ReviewSubmission s "
            + "where s.healerId in :healerIds and s.stage = :stage and s.status = :status group by s.healerId")
    List<Object[]> countByHealerIdsAndStageAndStatus(
            @Param("healerIds") Collection<UUID> healerIds,
            @Param("stage") ReviewStage stage,
            @Param("status") ReviewStatus status);

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---
    // Ids are selected as text and converted to UUID: a native UUID column comes back as byte[] on H2.

    @Query(value = "SELECT CAST(id AS VARCHAR(36)) FROM review_submission WHERE case_id IN (:ids)", nativeQuery = true)
    List<UUID> findIdsByCaseIds(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT CAST(id AS VARCHAR(36)) FROM review_submission WHERE healer_id = :healerId", nativeQuery = true)
    List<UUID> findIdsByHealerId(@Param("healerId") UUID healerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM review_submission_aud WHERE id IN (:ids)", nativeQuery = true)
    int eraseAuditByIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM review_submission WHERE id IN (:ids)", nativeQuery = true)
    int eraseByIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE review_submission SET reviewer_id = NULL WHERE reviewer_id = :healerId", nativeQuery = true)
    int clearReviewer(@Param("healerId") UUID healerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE review_submission SET created_by = NULL WHERE lower(created_by) = lower(:email)", nativeQuery = true)
    int clearCreatedBy(@Param("email") String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE review_submission SET updated_by = NULL WHERE lower(updated_by) = lower(:email)", nativeQuery = true)
    int clearUpdatedBy(@Param("email") String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE review_submission_aud SET created_by = NULL WHERE lower(created_by) = lower(:email)", nativeQuery = true)
    int clearCreatedByInHistory(@Param("email") String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE review_submission_aud SET updated_by = NULL WHERE lower(updated_by) = lower(:email)", nativeQuery = true)
    int clearUpdatedByInHistory(@Param("email") String email);
}
