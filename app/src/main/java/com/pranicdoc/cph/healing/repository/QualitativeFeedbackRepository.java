package com.pranicdoc.cph.healing.repository;

import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
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

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM qualitative_feedback_aud WHERE id IN (SELECT id FROM qualitative_feedback WHERE healing_session_id IN (:ids))", nativeQuery = true)
    int eraseAuditBySessionIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM qualitative_feedback WHERE healing_session_id IN (:ids)", nativeQuery = true)
    int eraseBySessionIds(@Param("ids") Collection<UUID> ids);
}
