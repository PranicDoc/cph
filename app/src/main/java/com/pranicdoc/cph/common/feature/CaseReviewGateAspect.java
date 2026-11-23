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
 * Lowest precedence, so it runs after method security ({@code @PreAuthorize}) and after request
 * binding and validation. Consequences: an unauthorized caller sees 403 (the route's existence is
 * not hidden from authenticated users), and a malformed body can return 400 even while the flag is
 * off. Only the route's existence leaks; no review data is reachable.
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
