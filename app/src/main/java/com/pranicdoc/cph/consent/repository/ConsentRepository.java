package com.pranicdoc.cph.consent.repository;

import com.pranicdoc.cph.consent.model.Consent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    /** CONSENT-06 — full history for a subject, most recent first (includes withdrawn/superseded). */
    List<Consent> findBySubjectIdOrderByAcceptedAtDesc(UUID subjectId);

    /**
     * CONSENT-04 — the subject's latest STILL-ACTIVE (not withdrawn) acceptance for a
     * given consent type. The AndWithdrawnAtIsNull clause is load-bearing: a withdrawn
     * record must not count as a held consent.
     */
    Optional<Consent> findTopBySubjectIdAndConsentTypeAndWithdrawnAtIsNullOrderByPolicyVersionDesc(
            UUID subjectId, String consentType);
}
