package com.pranicdoc.cph.common.model;

import org.hibernate.annotations.SQLRestriction;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reflection-only gate over every {@code @Entity} in the app, no Spring
 * context boot required (this repo's {@code @SpringBootTest} is broken
 * repo-wide — see {@code TenantFilterIntegrationTest}).
 * <p>
 * {@code @SQLRestriction("is_deleted = false")} is declared on {@link
 * BaseEntity}, a {@code @MappedSuperclass}. Hibernate does NOT inherit
 * class-level annotations like {@code @SQLRestriction} from a mapped
 * superclass — it only takes effect when repeated on the concrete {@code
 * @Entity} itself. Confirmed empirically (2026-08-26): with only the
 * superclass declaration, {@code org.hibernate.SQL=DEBUG} showed queries
 * against {@code role_permission}, {@code healer}, and {@code user_role}
 * carrying no {@code is_deleted} predicate at all — every soft-deleted row
 * in the app remained fully queryable. See
 * SOFT-DELETE-BLAST-RADIUS-SURVEY.md for the full investigation.
 * <p>
 * This test cannot prove the restriction actually filters rows at query
 * time (that needs a real Postgres integration test, which this repo has
 * no working harness for) — it only proves every entity declares it. That
 * is exactly the class of regression this bug was: the declaration existed
 * (on {@code BaseEntity}) and looked correct, but never took effect for a
 * single concrete entity.
 */
class SoftDeleteRestrictionCoverageTest {

    private static final String BASE_PACKAGE = "com.pranicdoc.cph";
    private static final String EXPECTED_RESTRICTION = "is_deleted = false";

    @Test
    void everyBaseEntitySubclassDeclaresItsOwnSqlRestriction() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(jakarta.persistence.Entity.class));
        Set<org.springframework.beans.factory.config.BeanDefinition> candidates =
                scanner.findCandidateComponents(BASE_PACKAGE);

        assertTrue(candidates.size() >= 30,
                "Expected at least 30 @Entity classes under " + BASE_PACKAGE
                        + " (found " + candidates.size() + ") — the scan itself may be broken");

        List<String> missing = new ArrayList<>();
        List<String> wrongValue = new ArrayList<>();

        for (org.springframework.beans.factory.config.BeanDefinition candidate : candidates) {
            Class<?> entityClass = Class.forName(candidate.getBeanClassName());
            if (!BaseEntity.class.isAssignableFrom(entityClass)) {
                continue;
            }

            SQLRestriction restriction = entityClass.getAnnotation(SQLRestriction.class);
            if (restriction == null) {
                missing.add(entityClass.getName());
            } else if (!EXPECTED_RESTRICTION.equals(restriction.value())) {
                wrongValue.add(entityClass.getName() + " has \"" + restriction.value() + "\"");
            }
        }

        assertTrue(missing.isEmpty(),
                "These @Entity classes extend BaseEntity but declare no @SQLRestriction of "
                        + "their own — soft-deleted rows of this type are NOT filtered from any "
                        + "query (BaseEntity's own @SQLRestriction does not propagate to "
                        + "subclasses, this is the exact 2026-08-26 regression): " + missing);

        assertTrue(wrongValue.isEmpty(),
                "These entities declare @SQLRestriction with an unexpected value — should be \""
                        + EXPECTED_RESTRICTION + "\": " + wrongValue);
    }
}
