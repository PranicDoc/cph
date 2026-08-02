package com.pranicdoc.cph.consent.model;

/**
 * Hardcoded enum of consent-gated document types (D-04, Phase 30).
 * Adding a new type is a code change (new constant), not a schema migration —
 * satisfies CONSENT-02. A fuller data-driven registry is deferred past this
 * milestone (D-05).
 */
public enum ConsentType {
    TERMS_AND_CONDITIONS,
    PRIVACY_POLICY,
    MEDICAL_DISCLAIMER,
    PRACTITIONER_AGREEMENT
}
