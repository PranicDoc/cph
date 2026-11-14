package com.pranicdoc.cph.review.repository;

import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
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

    long countByHealerIdAndStageAndStatus(UUID healerId, ReviewStage stage, ReviewStatus status);
}
