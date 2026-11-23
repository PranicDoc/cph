package com.pranicdoc.cph.common.feature;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Feature flags, evaluated per healer through Harness FME. The traffic key is the healer's UUID, never an
 * email or any other personal data, so the enabled users can be managed in the Harness console.
 * Safe default is off: a flag is on only when the treatment is exactly {@code on}; {@code off},
 * {@code control}, anything else, a missing healer, or any failure means off.
 * Without an SDK key no source exists and the configured default applies to everyone instead.
 */
@Slf4j
@Component
public class FeatureFlags {

    public static final String CASE_REVIEW_FLAG = "case_review_enabled";

    private static final String ON = "on";

    private final ObjectProvider<TreatmentSource> source;
    private final SecurityContextUtil securityContext;
    private final boolean caseReviewDefault;

    // Hand-written: @Value constructor parameter.
    public FeatureFlags(ObjectProvider<TreatmentSource> source, SecurityContextUtil securityContext,
                        @Value("${features.case-review.default:false}") boolean caseReviewDefault) {
        this.source = source;
        this.securityContext = securityContext;
        this.caseReviewDefault = caseReviewDefault;
    }

    /** Case review for the signed-in healer; off when nobody is signed in. */
    public boolean caseReviewEnabled() {
        return caseReviewEnabledFor(securityContext.getCurrentHealerIdOrNull());
    }

    /** Case review for the given healer, for callers outside the healer's own request (events, webhooks). */
    public boolean caseReviewEnabledFor(UUID healerId) {
        TreatmentSource treatments = source.getIfAvailable();
        if (treatments == null) {
            return caseReviewDefault;
        }
        if (healerId == null) {
            return false;
        }
        try {
            return ON.equals(treatments.treatment(healerId.toString(), CASE_REVIEW_FLAG));
        } catch (RuntimeException e) {
            log.warn("Flag {} evaluation failed, treating as off: {}", CASE_REVIEW_FLAG, e.toString());
            return false;
        }
    }
}
