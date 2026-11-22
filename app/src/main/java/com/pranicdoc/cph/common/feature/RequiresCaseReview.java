package com.pranicdoc.cph.common.feature;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller (or handler method) that exists only while the case-review feature flag is on.
 * While it is off the endpoint answers 404 as if it did not exist; see {@link CaseReviewGateAspect}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresCaseReview {
}
