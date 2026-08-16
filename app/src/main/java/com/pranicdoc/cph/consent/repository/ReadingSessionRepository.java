package com.pranicdoc.cph.consent.repository;

import com.pranicdoc.cph.consent.model.ReadingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReadingSessionRepository extends JpaRepository<ReadingSession, UUID> {

    /** Most recent reading sessions for a subject's given document version, newest first. */
    List<ReadingSession> findBySubjectIdAndConsentTypeAndPolicyVersionOrderByOpenedAtDesc(
            UUID subjectId, String consentType, int policyVersion);
}
