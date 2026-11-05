package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.QualitativeFeedback;
import com.pranicdoc.cph.common.enums.FeedbackType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QualitativeFeedbackRepository extends JpaRepository<QualitativeFeedback, UUID> {

    List<QualitativeFeedback> findByHealingSessionId(UUID sessionId);

    boolean existsByHealingSessionIdAndFeedbackType(UUID sessionId, FeedbackType feedbackType);

    /** A session's feedback of one type, newest first. Older data can hold more than one. */
    List<QualitativeFeedback> findByHealingSessionIdAndFeedbackTypeOrderByCreatedAtDesc(
            UUID sessionId, FeedbackType feedbackType);

    /**
     * A case's feedback of one type on any of its sessions, newest first. Used to keep to one FINAL
     * per case; a plain list, as older data can hold more than one and a single result would throw.
     */
    List<QualitativeFeedback> findByHealingSession_HealingCase_IdAndFeedbackTypeOrderByCreatedAtDesc(
            UUID caseId, FeedbackType feedbackType);
}
