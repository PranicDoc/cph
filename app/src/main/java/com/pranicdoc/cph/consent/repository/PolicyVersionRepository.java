package com.pranicdoc.cph.consent.repository;

import com.pranicdoc.cph.consent.model.ConsentType;
import com.pranicdoc.cph.consent.model.PolicyVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, UUID> {

    /** CONSENT-07 — active/current version of a policy; also the supersession comparison target for CONSENT-04. */
    Optional<PolicyVersion> findTopByConsentTypeOrderByPolicyVersionDesc(ConsentType consentType);

    /** Used by the accept path (30-02) to validate that the version being accepted actually exists (T-30-04). */
    Optional<PolicyVersion> findByConsentTypeAndPolicyVersion(ConsentType consentType, int policyVersion);
}
