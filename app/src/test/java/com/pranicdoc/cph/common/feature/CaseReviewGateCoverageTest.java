package com.pranicdoc.cph.common.feature;

import com.pranicdoc.cph.auth.controller.AuthController;
import com.pranicdoc.cph.review.controller.InternalPdfJobController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards against a new review endpoint shipping without the feature-flag gate. */
class CaseReviewGateCoverageTest {

    @Test
    void everyReviewRestControllerIsGated() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> ungated = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.pranicdoc.cph.review")) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            // InternalPdfJobController answers 204 itself instead of 404 (it is not user-facing).
            if (type == InternalPdfJobController.class) {
                continue;
            }
            if (!type.isAnnotationPresent(RequiresCaseReview.class)) {
                ungated.add(type.getSimpleName());
            }
        }
        assertThat(ungated).as("review controllers missing @RequiresCaseReview").isEmpty();
    }

    @Test
    void reviewPreferencesEndpointIsGated() throws NoSuchMethodException {
        var method = AuthController.class.getMethod("updateReviewPreferences",
                com.pranicdoc.cph.auth.dto.ReviewPreferencesRequest.class);
        assertThat(method.isAnnotationPresent(RequiresCaseReview.class)).isTrue();
    }
}
