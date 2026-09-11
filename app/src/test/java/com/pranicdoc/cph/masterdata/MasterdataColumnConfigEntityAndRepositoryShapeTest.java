package com.pranicdoc.cph.masterdata;

import com.pranicdoc.cph.common.model.BaseEntity;
import com.pranicdoc.cph.masterdata.model.MasterdataColumnConfig;
import com.pranicdoc.cph.masterdata.repository.MasterdataColumnConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain-JUnit (no Spring context) offline substitute for Spring Data's
 * normal derived-query-method property-path validation, adapted from
 * {@code RbacRepositoryQueryDerivationTest} for the single
 * {@link MasterdataColumnConfigRepository} / {@link MasterdataColumnConfig}
 * pair. Full Spring application-context boot (needed for
 * {@code @DataJpaTest}) is currently broken repo-wide (see
 * {@code TenantFilterIntegrationTest}'s {@code PropertyPlaceholderHelper}
 * failure), so this class re-implements the equivalent property-path
 * resolution offline.
 */
class MasterdataColumnConfigEntityAndRepositoryShapeTest {

    /** Longest-prefix-first so "findAllBy" is tried before "findBy". */
    private static final List<String> QUERY_KEYWORDS = List.of(
            "findAllBy", "findBy", "existsBy", "countBy", "deleteBy");

    private static final List<String> TRAILING_KEYWORDS = List.of(
            "IsNotNull", "IgnoreCase", "Containing", "IsNull", "Between", "True", "False", "In");

    private static final Pattern ORDER_BY_PATTERN =
            Pattern.compile("^(.*)OrderBy([A-Z][A-Za-z0-9]*)(Asc|Desc)$");

    @Test
    void bothDerivedQueryMethodsResolveToRealEntityProperties() {
        Set<String> properties = declaredPropertyNames(MasterdataColumnConfig.class);

        for (Method method : MasterdataColumnConfigRepository.class.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }
            String keyword = matchingKeyword(method.getName());
            if (keyword == null) {
                continue; // not a derived-query method shape — nothing to validate
            }
            String subject = method.getName().substring(keyword.length());

            Matcher orderByMatcher = ORDER_BY_PATTERN.matcher(subject);
            String orderByProperty = null;
            if (orderByMatcher.matches()) {
                subject = orderByMatcher.group(1);
                orderByProperty = orderByMatcher.group(2);
            }

            if (orderByProperty != null) {
                String resolved = uncapitalize(stripTrailingKeywords(orderByProperty));
                String finalOrderByProperty = orderByProperty;
                assertTrue(properties.contains(resolved),
                        () -> method.getName() + " — OrderBy segment '" + finalOrderByProperty
                                + "' does not resolve to a property of MasterdataColumnConfig");
            }

            if (!subject.isEmpty()) {
                for (String segment : splitAndOr(subject)) {
                    String resolved = uncapitalize(stripTrailingKeywords(segment));
                    String finalSegment = segment;
                    assertTrue(properties.contains(resolved),
                            () -> method.getName() + " — segment '" + finalSegment + "' (resolved: '" + resolved
                                    + "') does not resolve to a property of MasterdataColumnConfig");
                }
            }
        }
    }

    @Test
    void repositoryIsParameterizedWithMasterdataColumnConfigAndUuid() {
        Class<?> entityType = resolveEntityType(MasterdataColumnConfigRepository.class);
        Class<?> idType = resolveIdType(MasterdataColumnConfigRepository.class);
        assertEquals(MasterdataColumnConfig.class, entityType,
                "MasterdataColumnConfigRepository must be JpaRepository<MasterdataColumnConfig, ...>");
        assertEquals(UUID.class, idType,
                "MasterdataColumnConfigRepository must be JpaRepository<..., UUID> like every other repository in this codebase");
    }

    @Test
    void neitherDerivedQueryMethodDeclaresQueryOrModifying() {
        for (Method method : MasterdataColumnConfigRepository.class.getDeclaredMethods()) {
            assertFalse(method.isAnnotationPresent(Query.class),
                    method.getName() + " must not declare @Query — derived queries only, matching ConsentTypeRepository's convention");
            assertFalse(method.isAnnotationPresent(Modifying.class),
                    method.getName() + " must not declare @Modifying — derived queries only, matching ConsentTypeRepository's convention");
        }
    }

    @Test
    void entityHasNoActiveFieldPitfall1Guard() {
        for (Field field : MasterdataColumnConfig.class.getDeclaredFields()) {
            assertFalse("active".equals(field.getName()),
                    "Pitfall 1: MasterdataColumnConfig must not carry a dedicated 'active' flag — "
                            + "MDIMPORT-03 requires real removal via BaseEntity's inherited soft-delete, "
                            + "not ConsentType's no-removal carve-out");
        }
    }

    @Test
    void declaredFieldSetIsExactlyTheSixConfigFields() {
        Set<String> declared = new LinkedHashSet<>();
        for (Field field : MasterdataColumnConfig.class.getDeclaredFields()) {
            if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
                declared.add(field.getName());
            }
        }
        assertEquals(
                Set.of("masterdataType", "columnName", "targetField", "required", "dataType", "systemDefined"),
                declared,
                "MasterdataColumnConfig's declared field set must match V37's columns 1:1, no more, no less");
    }

    // -------------------------------------------------------------------
    // Helpers — adapted verbatim from RbacRepositoryQueryDerivationTest.
    // -------------------------------------------------------------------

    private static String matchingKeyword(String methodName) {
        for (String keyword : QUERY_KEYWORDS) {
            if (methodName.startsWith(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    private static List<String> splitAndOr(String subject) {
        String[] parts = subject.split("(?<=[a-z0-9])And(?=[A-Z])|(?<=[a-z0-9])Or(?=[A-Z])");
        return List.of(parts);
    }

    private static String stripTrailingKeywords(String segment) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String trailing : TRAILING_KEYWORDS) {
                if (segment.length() > trailing.length() && segment.endsWith(trailing)) {
                    segment = segment.substring(0, segment.length() - trailing.length());
                    changed = true;
                }
            }
        }
        return segment;
    }

    private static String uncapitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static Class<?> resolveEntityType(Class<?> repoInterface) {
        return resolveTypeArgument(repoInterface, 0);
    }

    private static Class<?> resolveIdType(Class<?> repoInterface) {
        return resolveTypeArgument(repoInterface, 1);
    }

    private static Class<?> resolveTypeArgument(Class<?> repoInterface, int index) {
        for (Type genericInterface : repoInterface.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType parameterizedType
                    && parameterizedType.getRawType() == JpaRepository.class) {
                Type arg = parameterizedType.getActualTypeArguments()[index];
                assertTrue(arg instanceof Class<?>, repoInterface.getSimpleName() + " JpaRepository type argument is not a resolvable Class");
                return (Class<?>) arg;
            }
        }
        throw new AssertionError(repoInterface.getSimpleName() + " does not directly extend JpaRepository<Entity, ID>");
    }

    /** Declared field names (property paths) of {@code entityClass} plus {@link BaseEntity}, skipping static/synthetic fields. */
    private static Set<String> declaredPropertyNames(Class<?> entityClass) {
        Set<String> names = new LinkedHashSet<>();
        for (Field field : entityClass.getDeclaredFields()) {
            addPropertyName(names, field);
        }
        for (Field field : BaseEntity.class.getDeclaredFields()) {
            addPropertyName(names, field);
        }
        return names;
    }

    private static void addPropertyName(Set<String> names, Field field) {
        if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
            return;
        }
        names.add(field.getName());
    }
}
