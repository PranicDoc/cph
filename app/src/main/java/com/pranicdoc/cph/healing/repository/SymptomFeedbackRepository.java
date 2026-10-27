package com.pranicdoc.cph.healing.repository;

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
}
