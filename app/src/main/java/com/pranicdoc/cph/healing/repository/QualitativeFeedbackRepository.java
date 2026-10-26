package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.QualitativeFeedback;
import com.pranicdoc.cph.common.enums.FeedbackType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QualitativeFeedbackRepository extends JpaRepository<QualitativeFeedback, UUID> {

    List<QualitativeFeedback> findByHealingSessionId(UUID sessionId);

    boolean existsByHealingSessionIdAndFeedbackType(UUID sessionId, FeedbackType feedbackType);

    /**
     * Check if a FINAL feedback already exists for any session in this case.
     * Used to enforce the partial unique constraint (1 final per case).
     */
    Optional<QualitativeFeedback> findByHealingSession_HealingCase_IdAndFeedbackType(
            UUID caseId, FeedbackType feedbackType);
}
