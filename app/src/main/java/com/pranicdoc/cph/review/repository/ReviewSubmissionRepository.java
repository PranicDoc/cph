package com.pranicdoc.cph.review.repository;

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
}
