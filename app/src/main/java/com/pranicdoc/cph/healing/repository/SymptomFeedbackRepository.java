package com.pranicdoc.cph.healing.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.pranicdoc.cph.healing.model.SymptomFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SymptomFeedbackRepository extends JpaRepository<SymptomFeedback, UUID> {

    List<SymptomFeedback> findByHealingSessionId(UUID sessionId);

    /** Removes the feedback given on symptoms that no longer exist on the case. */
    void deleteByCaseSymptomIdIn(Collection<UUID> caseSymptomIds);

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM symptom_feedback_aud WHERE id IN (SELECT id FROM symptom_feedback WHERE healing_session_id IN (:ids))", nativeQuery = true)
    int eraseAuditBySessionIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM symptom_feedback WHERE healing_session_id IN (:ids)", nativeQuery = true)
    int eraseBySessionIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM symptom_feedback_aud WHERE id IN (SELECT id FROM symptom_feedback WHERE case_symptom_id IN (:ids))", nativeQuery = true)
    int eraseAuditByCaseSymptomIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM symptom_feedback WHERE case_symptom_id IN (:ids)", nativeQuery = true)
    int eraseByCaseSymptomIds(@Param("ids") Collection<UUID> ids);
}
