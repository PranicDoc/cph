package com.pranicdoc.cph.common.feature;

import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Answers 404 for {@link RequiresCaseReview} endpoints while the case-review flag is off.
 * Lowest precedence so method security ({@code @PreAuthorize}) runs first: an unauthorized
 * caller still gets 401/403 rather than learning the feature exists via 404.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class CaseReviewGateAspect {

    private final FeatureFlags featureFlags;

    @Before("@within(com.pranicdoc.cph.common.feature.RequiresCaseReview)"
            + " || @annotation(com.pranicdoc.cph.common.feature.RequiresCaseReview)")
    public void gate() {
        if (!featureFlags.caseReviewEnabled()) {
            throw new ResourceNotFoundException("Not found");
        }
    }
}
