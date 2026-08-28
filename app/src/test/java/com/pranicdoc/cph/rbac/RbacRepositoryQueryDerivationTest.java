package com.pranicdoc.cph.rbac;

import com.pranicdoc.cph.common.model.BaseEntity;
import com.pranicdoc.cph.rbac.repository.RbacRoleRepository;
import com.pranicdoc.cph.rbac.repository.RolePermissionRepository;
import com.pranicdoc.cph.rbac.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
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
 * normal derived-query-method property-path validation.
 * <p>
 * Spring Data JPA validates every derived-query method name against its
 * entity's declared properties at application-context startup, failing fast
 * on a typo such as {@code findAllByPrincipalKind} when the entity property
 * is actually {@code principalId}. In this repository full Spring
 * application-context boot is currently broken
 * ({@code TenantFilterIntegrationTest} fails at
 * {@code PropertyPlaceholderHelper}), so that validation never runs for the
 * rbac repositories — a mistyped method would compile cleanly, ship
 * silently, and only surface as a runtime exception the first time
 * something actually calls it (potentially inside Phase 36's authorization
 * decision path). This class re-implements the equivalent property-path
 * resolution offline, against the three rbac repository interfaces, with no
 * Spring context and no new dependency.
 */
class RbacRepositoryQueryDerivationTest {

    private static final List<Class<?>> REPOSITORIES = List.of(
            RbacRoleRepository.class,
            RolePermissionRepository.class,
            UserRoleRepository.class);

    /** Longest-prefix-first so "findAllBy" is tried before "findBy". */
    private static final List<String> QUERY_KEYWORDS = List.of(
            "findAllBy", "findBy", "existsBy", "countBy", "deleteBy");

    private static final List<String> TRAILING_KEYWORDS = List.of(
            "IsNotNull", "IgnoreCase", "Containing", "IsNull", "Between", "True", "False", "In");

    private static final Pattern ORDER_BY_PATTERN =
            Pattern.compile("^(.*)OrderBy([A-Z][A-Za-z0-9]*)(Asc|Desc)$");

    // -------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------

    @Test
    void everyDerivedQueryResolvesToAnEntityProperty() {
        for (Class<?> repoInterface : REPOSITORIES) {
            Class<?> entityClass = resolveEntityType(repoInterface);
            Set<String> properties = declaredPropertyNames(entityClass);

            for (Method method : repoInterface.getDeclaredMethods()) {
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
                            () -> repoInterface.getSimpleName() + "." + method.getName()
                                    + " — OrderBy segment '" + finalOrderByProperty
                                    + "' does not resolve to a property of " + entityClass.getSimpleName());
                }

                if (!subject.isEmpty()) {
                    for (String segment : splitAndOr(subject)) {
                        String resolved = uncapitalize(stripTrailingKeywords(segment));
                        String finalSegment = segment;
                        assertTrue(properties.contains(resolved),
                                () -> repoInterface.getSimpleName() + "." + method.getName()
                                        + " — segment '" + finalSegment + "' (resolved: '" + resolved
                                        + "') does not resolve to a property of " + entityClass.getSimpleName());
                    }
                }
            }
        }
    }

    @Test
    void everyRepositoryIsParameterisedWithUuidId() {
        for (Class<?> repoInterface : REPOSITORIES) {
            Class<?> idType = resolveIdType(repoInterface);
            assertEquals(UUID.class, idType,
                    repoInterface.getSimpleName() + " must be JpaRepository<Entity, UUID> like every other repository in this codebase");
        }
    }

    /**
     * D-08 (Phase 38, ROLE-01) explicit coverage for the paged, case-insensitive
     * name search that backs the server-paginated {@code GET /api/admin/roles}.
     * The generic sweep above already resolves its {@code Name} subject, but this
     * pins the exact shape the controller + service depend on: the method exists
     * with {@code (String, Pageable)}, returns a Spring Data {@code Page} (never a
     * bare {@code List}, so the endpoint can actually paginate), its derived
     * subject resolves to a real {@code RbacRole} property, and it carries no
     * {@code @Query}/{@code @Modifying} (this module is derived-queries-only).
     */
    @Test
    void rbacRoleRepositoryDeclaresPagedCaseInsensitiveNameSearch() throws Exception {
        Method method = RbacRoleRepository.class.getDeclaredMethod(
                "findByNameContainingIgnoreCase", String.class, Pageable.class);

        assertEquals(Page.class, method.getReturnType(),
                "findByNameContainingIgnoreCase must return Page<RbacRole> so GET /api/admin/roles can paginate server-side");

        Class<?> entityClass = resolveEntityType(RbacRoleRepository.class);
        Set<String> properties = declaredPropertyNames(entityClass);
        assertTrue(properties.contains("name"),
                "findByNameContainingIgnoreCase — subject 'Name' (after stripping Containing/IgnoreCase) must resolve to an RbacRole property");

        assertFalse(method.isAnnotationPresent(Query.class),
                "findByNameContainingIgnoreCase must stay a derived query — no @Query in the rbac module");
        assertFalse(method.isAnnotationPresent(Modifying.class),
                "findByNameContainingIgnoreCase must stay a derived query — no @Modifying in the rbac module");
    }

    @Test
    void noRepositoryDeclaresACustomQuery() {
        for (Class<?> repoInterface : REPOSITORIES) {
            for (Method method : repoInterface.getDeclaredMethods()) {
                assertFalse(method.isAnnotationPresent(Query.class),
                        repoInterface.getSimpleName() + "." + method.getName() + " must not declare @Query — this module is derived-queries-only in Phase 35");
                assertFalse(method.isAnnotationPresent(Modifying.class),
                        repoInterface.getSimpleName() + "." + method.getName() + " must not declare @Modifying — this module is derived-queries-only in Phase 35");
            }
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private static String matchingKeyword(String methodName) {
        for (String keyword : QUERY_KEYWORDS) {
            if (methodName.startsWith(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    /**
     * Splits a derived-query subject on {@code And}/{@code Or} boundaries —
     * only where the connector is preceded by a lowercase letter/digit and
     * followed by an uppercase letter, so a property name can never be
     * mistaken for a connector.
     */
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
