package com.pranicdoc.cph.masterdata.controller;

import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Annotation-level authorization contract for {@link MasterDataController} after
 * the Phase 40 bare-form cutover (plan 40-05, D-02/D-04/D-09, PD-40-03/PD-40-04).
 *
 * <p>This is pure reflection over {@code @PreAuthorize} annotations plus
 * {@link Resource}/{@link Operation} enum validity — no Spring context, no
 * {@code @WebMvcTest}, no filter chain. The request-level (real filter chain)
 * allow/deny suite is plan 40-07's job; this file only pins what the
 * annotations say.
 *
 * <ul>
 *   <li><b>Section A</b> — 20 independent literal pins, one per endpoint.</li>
 *   <li><b>Section B</b> — anti-vacuous sweep: every request-mapped method carries
 *       a bare {@code @rbacAuthz.hasPermission('R','V')} gate, {@code inspected == 20}.</li>
 *   <li><b>Section C</b> — every named (resource, verb) pair is allow-listed.</li>
 *   <li><b>Section D</b> — PD-40-04: the master-data ADMIN override verb is
 *       intentionally non-grantable; adding it fails this test.</li>
 *   <li><b>Section E</b> — the 7 system-row override flags were re-sourced, not
 *       deleted: comment-stripped source has zero SUPADM literals and exactly 7
 *       {@code boolean isSuperAdmin}.</li>
 * </ul>
 */
class MasterDataControllerAuthorizationContractTest {

    // ─── expected literals (several endpoints share a value; each is pinned independently) ──

    private static final String CHAKRAS_READ    = "@rbacAuthz.hasPermission('MASTER_DATA_CHAKRAS','READ')";
    private static final String CHAKRAS_CREATE  = "@rbacAuthz.hasPermission('MASTER_DATA_CHAKRAS','CREATE')";
    private static final String CHAKRAS_UPDATE  = "@rbacAuthz.hasPermission('MASTER_DATA_CHAKRAS','UPDATE')";
    private static final String CHAKRAS_DELETE  = "@rbacAuthz.hasPermission('MASTER_DATA_CHAKRAS','DELETE')";

    private static final String COLORS_READ     = "@rbacAuthz.hasPermission('MASTER_DATA_COLORS','READ')";
    private static final String COLORS_CREATE   = "@rbacAuthz.hasPermission('MASTER_DATA_COLORS','CREATE')";
    private static final String COLORS_UPDATE   = "@rbacAuthz.hasPermission('MASTER_DATA_COLORS','UPDATE')";
    private static final String COLORS_DELETE   = "@rbacAuthz.hasPermission('MASTER_DATA_COLORS','DELETE')";

    private static final String PROTOCOLS_READ   = "@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','READ')";
    private static final String PROTOCOLS_CREATE = "@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','CREATE')";
    private static final String PROTOCOLS_UPDATE = "@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','UPDATE')";
    private static final String PROTOCOLS_DELETE = "@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','DELETE')";

    /** methodName -> expected @PreAuthorize literal. All 20 endpoints. */
    private static final List<String[]> EXPECTED_GATES = List.of(
            new String[]{"getChakras",      CHAKRAS_READ},
            new String[]{"getChakraById",   CHAKRAS_READ},
            new String[]{"getChakraByCode", CHAKRAS_READ},
            new String[]{"createChakra",    CHAKRAS_CREATE},
            new String[]{"updateChakra",    CHAKRAS_UPDATE},
            new String[]{"deleteChakra",    CHAKRAS_DELETE},
            new String[]{"getColors",       COLORS_READ},
            new String[]{"getColorById",    COLORS_READ},
            new String[]{"getColorByCode",  COLORS_READ},
            new String[]{"createColor",     COLORS_CREATE},
            new String[]{"updateColor",     COLORS_UPDATE},
            new String[]{"deleteColor",     COLORS_DELETE},
            new String[]{"getMappings",     PROTOCOLS_READ},
            new String[]{"createMappings",  PROTOCOLS_CREATE},
            new String[]{"deleteMapping",   PROTOCOLS_DELETE},
            new String[]{"getProtocols",    PROTOCOLS_READ},
            new String[]{"getProtocolById", PROTOCOLS_READ},
            new String[]{"createProtocol",  PROTOCOLS_CREATE},
            new String[]{"updateProtocol",  PROTOCOLS_UPDATE},
            new String[]{"deleteProtocol",  PROTOCOLS_DELETE});

    private static String preAuthorizeValueOf(Class<?> controller, String methodName) {
        Method match = null;
        for (Method m : controller.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic() || !m.getName().equals(methodName)) {
                continue;
            }
            if (match != null) {
                return fail("method %s on %s is ambiguous (overloaded) — cannot pin its @PreAuthorize",
                        methodName, controller.getSimpleName());
            }
            match = m;
        }
        if (match == null) {
            return fail("no public method named %s on %s", methodName, controller.getSimpleName());
        }
        PreAuthorize preAuthorize = match.getAnnotation(PreAuthorize.class);
        if (preAuthorize == null) {
            return fail("method %s on %s carries no @PreAuthorize", methodName, controller.getSimpleName());
        }
        return preAuthorize.value();
    }

    private static void assertGate(String methodName, String expected) {
        assertThat(preAuthorizeValueOf(MasterDataController.class, methodName))
                .as("%s must be gated by %s", methodName, expected)
                .isEqualTo(expected);
    }

    // ─── Section A — 20 independent literal pins ──────────────────────────

    @Test @DisplayName("chakras: getChakras is gated MASTER_DATA_CHAKRAS:READ (D-09 newly gated)")
    void getChakras_isGatedByChakrasRead() { assertGate("getChakras", CHAKRAS_READ); }

    @Test @DisplayName("chakras: getChakraById is gated MASTER_DATA_CHAKRAS:READ (D-09 newly gated)")
    void getChakraById_isGatedByChakrasRead() { assertGate("getChakraById", CHAKRAS_READ); }

    @Test @DisplayName("chakras: getChakraByCode is gated MASTER_DATA_CHAKRAS:READ (D-09 newly gated)")
    void getChakraByCode_isGatedByChakrasRead() { assertGate("getChakraByCode", CHAKRAS_READ); }

    @Test @DisplayName("chakras: createChakra is gated MASTER_DATA_CHAKRAS:CREATE")
    void createChakra_isGatedByChakrasCreate() { assertGate("createChakra", CHAKRAS_CREATE); }

    @Test @DisplayName("chakras: updateChakra is gated MASTER_DATA_CHAKRAS:UPDATE")
    void updateChakra_isGatedByChakrasUpdate() { assertGate("updateChakra", CHAKRAS_UPDATE); }

    @Test @DisplayName("chakras: deleteChakra is gated MASTER_DATA_CHAKRAS:DELETE")
    void deleteChakra_isGatedByChakrasDelete() { assertGate("deleteChakra", CHAKRAS_DELETE); }

    @Test @DisplayName("colors: getColors is gated MASTER_DATA_COLORS:READ (D-09 newly gated)")
    void getColors_isGatedByColorsRead() { assertGate("getColors", COLORS_READ); }

    @Test @DisplayName("colors: getColorById is gated MASTER_DATA_COLORS:READ (D-09 newly gated)")
    void getColorById_isGatedByColorsRead() { assertGate("getColorById", COLORS_READ); }

    @Test @DisplayName("colors: getColorByCode is gated MASTER_DATA_COLORS:READ (D-09 newly gated)")
    void getColorByCode_isGatedByColorsRead() { assertGate("getColorByCode", COLORS_READ); }

    @Test @DisplayName("colors: createColor is gated MASTER_DATA_COLORS:CREATE")
    void createColor_isGatedByColorsCreate() { assertGate("createColor", COLORS_CREATE); }

    @Test @DisplayName("colors: updateColor is gated MASTER_DATA_COLORS:UPDATE")
    void updateColor_isGatedByColorsUpdate() { assertGate("updateColor", COLORS_UPDATE); }

    @Test @DisplayName("colors: deleteColor is gated MASTER_DATA_COLORS:DELETE")
    void deleteColor_isGatedByColorsDelete() { assertGate("deleteColor", COLORS_DELETE); }

    @Test @DisplayName("mappings: getMappings is gated MASTER_DATA_PROTOCOLS:READ (PD-40-03; conversion, not newly gated)")
    void getMappings_isGatedByProtocolsRead() { assertGate("getMappings", PROTOCOLS_READ); }

    @Test @DisplayName("mappings: createMappings is gated MASTER_DATA_PROTOCOLS:CREATE (PD-40-03)")
    void createMappings_isGatedByProtocolsCreate() { assertGate("createMappings", PROTOCOLS_CREATE); }

    @Test @DisplayName("mappings: deleteMapping is gated MASTER_DATA_PROTOCOLS:DELETE (PD-40-03)")
    void deleteMapping_isGatedByProtocolsDelete() { assertGate("deleteMapping", PROTOCOLS_DELETE); }

    @Test @DisplayName("protocols: getProtocols is gated MASTER_DATA_PROTOCOLS:READ (D-09 newly gated)")
    void getProtocols_isGatedByProtocolsRead() { assertGate("getProtocols", PROTOCOLS_READ); }

    @Test @DisplayName("protocols: getProtocolById is gated MASTER_DATA_PROTOCOLS:READ (D-09 newly gated)")
    void getProtocolById_isGatedByProtocolsRead() { assertGate("getProtocolById", PROTOCOLS_READ); }

    @Test @DisplayName("protocols: createProtocol is gated MASTER_DATA_PROTOCOLS:CREATE")
    void createProtocol_isGatedByProtocolsCreate() { assertGate("createProtocol", PROTOCOLS_CREATE); }

    @Test @DisplayName("protocols: updateProtocol is gated MASTER_DATA_PROTOCOLS:UPDATE")
    void updateProtocol_isGatedByProtocolsUpdate() { assertGate("updateProtocol", PROTOCOLS_UPDATE); }

    @Test @DisplayName("protocols: deleteProtocol is gated MASTER_DATA_PROTOCOLS:DELETE")
    void deleteProtocol_isGatedByProtocolsDelete() { assertGate("deleteProtocol", PROTOCOLS_DELETE); }

    // ─── Section B — anti-vacuous sweep ──────────────────────────────────

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    private static final Pattern BARE_GATE =
            Pattern.compile("^@rbacAuthz\\.hasPermission\\('([A-Z_]+)','([A-Z]+)'\\)$");

    @Test
    void everyRequestMappedMethodCarriesABareDynamicGate() {
        int inspected = 0;

        for (Method method : MasterDataController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                continue;
            }
            boolean isRequestMapped = REQUEST_MAPPING_ANNOTATIONS.stream()
                    .anyMatch(annotationType -> method.getAnnotation(annotationType) != null);
            if (!isRequestMapped) {
                continue;
            }

            inspected++;

            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
            assertThat(preAuthorize)
                    .as("method %s must carry @PreAuthorize", method.getName())
                    .isNotNull();

            String value = preAuthorize.value();
            assertThat(value)
                    .as("method %s's @PreAuthorize must be a bare @rbacAuthz.hasPermission('R','V') expression",
                            method.getName())
                    .matches(BARE_GATE);
            assertThat(value)
                    .as("method %s's @PreAuthorize must not retain a hasRole gate", method.getName())
                    .doesNotContain("hasRole");
            assertThat(value)
                    .as("method %s's @PreAuthorize must not retain a hasAnyRole gate", method.getName())
                    .doesNotContain("hasAnyRole");
        }

        assertThat(inspected)
                .as("the sweep must inspect exactly the 20 declared MasterDataController endpoints. "
                        + "Eight of these (three chakra GETs, three colour GETs, two protocol GETs) were "
                        + "ungated before this phase (D-09); a regression that drops one of those gates "
                        + "restores an information-disclosure path — internal catalog keys and system-row "
                        + "structure become readable by any authenticated caller again — rather than merely "
                        + "failing closed.")
                .isEqualTo(20);
    }

    // ─── Section C — allow-list validity for all 20 pairs ────────────────

    @Test
    void everyGateNamesAnAllowListedResourceVerbPair() {
        for (String[] entry : EXPECTED_GATES) {
            String methodName = entry[0];
            String literal = preAuthorizeValueOf(MasterDataController.class, methodName);

            Matcher matcher = BARE_GATE.matcher(literal);
            assertThat(matcher.matches())
                    .as("gate for %s (%s) must be a bare @rbacAuthz.hasPermission('R','V') expression",
                            methodName, literal)
                    .isTrue();

            // An unknown literal must fail the test here, not be silently skipped.
            Resource resource = Resource.valueOf(matcher.group(1));
            Operation operation = Operation.valueOf(matcher.group(2));

            assertThat(resource.supports(operation))
                    .as("%s: %s:%s is not an allow-listed (resource, verb) pair (D-10) — RbacAuthz would "
                            + "resolve it to false for every non-SUPADM caller, permanently",
                            methodName, resource, operation)
                    .isTrue();
        }
    }

    // ─── Section D — PD-40-04: the master-data ADMIN override is non-grantable by design ──

    @Test
    void masterDataOverrideVerbIsIntentionallyNonGrantable() {
        String rationale =
                "PD-40-04: the master-data system-row override is SUPADM-only-forever and NON-grantable. "
                        + "The three MASTER_DATA_* resources deliberately do not allow-list Operation.ADMIN, "
                        + "so a MASTER_DATA_*+ADMIN grant can never exist and rbacAuthz.hasPermission("
                        + "\"MASTER_DATA_*\",\"ADMIN\") is true only for SUPADM (via the short-circuit) — which "
                        + "is exactly the pre-cutover behaviour. Contrast LEGAL_DOCUMENTS:ADMIN, which IS "
                        + "allow-listed and therefore a genuinely grantable consent override. If you are here "
                        + "because you added Operation.ADMIN to a MASTER_DATA_* constant: that is a Phase-35 "
                        + "catalog change with RbacCatalogService / catalog-test / Phase-38 role-editor UI "
                        + "fallout. Make it a conscious decision, do not just silence this test.";

        assertThat(Resource.MASTER_DATA_CHAKRAS.supports(Operation.ADMIN)).as(rationale).isFalse();
        assertThat(Resource.MASTER_DATA_COLORS.supports(Operation.ADMIN)).as(rationale).isFalse();
        assertThat(Resource.MASTER_DATA_PROTOCOLS.supports(Operation.ADMIN)).as(rationale).isFalse();

        assertThat(Resource.LEGAL_DOCUMENTS.supports(Operation.ADMIN))
                .as("LEGAL_DOCUMENTS:ADMIN IS allow-listed — the grantable counterpart the master-data "
                        + "override is deliberately NOT (PD-40-04)")
                .isTrue();
    }

    // ─── Section E — the 7 override flags were re-sourced, not deleted ───

    private static final Pattern BLOCK_COMMENT = Pattern.compile("(?s)/\\*.*?\\*/");
    private static final Pattern LINE_COMMENT = Pattern.compile("//.*");

    @Test
    void noCapabilityFlagUsesTheSupadmLiteral() {
        Path controllerSource = Paths.get(
                "src", "main", "java", "com", "pranicdoc", "cph", "masterdata", "controller",
                "MasterDataController.java");
        assertThat(Files.isRegularFile(controllerSource))
                .as("expected MasterDataController.java at %s", controllerSource.toAbsolutePath())
                .isTrue();

        String source;
        try {
            source = Files.readString(controllerSource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String stripped = LINE_COMMENT.matcher(
                        BLOCK_COMMENT.matcher(source).replaceAll(""))
                .replaceAll("");

        assertThat(stripped)
                .as("no SUPADM role literal may survive in MasterDataController executable code — "
                        + "the D-04 override must be sourced from RbacAuthz, not a hardcoded role compare")
                .doesNotContain("\"SUPADM\"");

        int flagCount = countOccurrences(stripped, "boolean isSuperAdmin");
        assertThat(flagCount)
                .as("all 7 system-row override flags must still be computed and passed to the service — "
                        + "deleting them (or replacing with a literal true) would hand the override to every "
                        + "caller who clears the coarse gate (T-40-21)")
                .isEqualTo(7);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = haystack.indexOf(needle);
        while (idx != -1) {
            count++;
            idx = haystack.indexOf(needle, idx + needle.length());
        }
        return count;
    }
}
