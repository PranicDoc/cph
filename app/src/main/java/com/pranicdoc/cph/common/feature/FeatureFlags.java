package com.pranicdoc.cph.common.feature;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Global feature flags, evaluated through Harness FME. Safe default is off: a flag is on only when the
 * treatment is exactly {@code on}; {@code off}, {@code control}, anything else, or any failure means off.
 * Without an SDK key no source exists and the configured default applies instead.
 */
@Slf4j
@Component
public class FeatureFlags {

    public static final String CASE_REVIEW_FLAG = "case_review_enabled";

    /** Flags are global, so one constant traffic key; no user identity goes to Harness. */
    private static final String TRAFFIC_KEY = "cph";

    private static final String ON = "on";

    private final ObjectProvider<TreatmentSource> source;
    private final boolean caseReviewDefault;

    // Hand-written: @Value constructor parameter.
    public FeatureFlags(ObjectProvider<TreatmentSource> source,
                        @Value("${features.case-review.default:false}") boolean caseReviewDefault) {
        this.source = source;
        this.caseReviewDefault = caseReviewDefault;
    }

    public boolean caseReviewEnabled() {
        TreatmentSource treatments = source.getIfAvailable();
        if (treatments == null) {
            return caseReviewDefault;
        }
        try {
            return ON.equals(treatments.treatment(TRAFFIC_KEY, CASE_REVIEW_FLAG));
        } catch (RuntimeException e) {
            log.warn("Flag {} evaluation failed, treating as off: {}", CASE_REVIEW_FLAG, e.toString());
            return false;
        }
    }
}
