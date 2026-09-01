package com.pranicdoc.cph.healing.controller;

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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Annotation-level authorization contract for {@link CaseController} after the Phase 42
 * Wave-2 capability-gate cutover (plan 42-03, SC1 / SC4, D-05 / D-06 / D-16 / D-19).
 *
 * <p>{@code CaseController} is the largest and most concern-mixed surface in the phase —
 * 18 request-mapped methods spanning case CRUD, the PDF job pipeline and the patient
 * feedback cycle. A 14-site split by hand is exactly where a copy-paste
 * {@code (resource, verb)} error hides, and where accidentally gating a patient-reachable
 * endpoint would 403 the live patient health-form and feedback flows. This test pins what
 * the annotations now say — pure reflection over {@code @PreAuthorize} plus
 * {@link Resource}/{@link Operation} enum validity. No Spring context, no
 * {@code @WebMvcTest}, no {@code MockMvc}: it cannot pass for filter-chain reasons and
 * runs in milliseconds. The request-time (real filter chain) allow/deny suite is plan
 * 42-09.
 *
 * <ul>
 *   <li><b>Section A</b> — 14 independent literal pins, one per gated method.</li>
 *   <li><b>Section B</b> — anti-vacuous over-reach guard: inspected == 18, gated == 14,
 *       ungated == {create, getById, updateCaseDetails, getSymptomsForFeedback}.</li>
 *   <li><b>Section C</b> — the 14 literals resolve through the {@link Resource}/
 *       {@link Operation} allow-list into exactly the {CASE, PDF, FEEDBACK} partition in
 *       a 10/3/1 split; {@code FEEDBACK:READ} and {@code PDF:UPDATE} are proven
 *       non-allow-listed.</li>
 * </ul>
 */
class CaseControllerAuthorizationContractTest {

    // ─── Section A expected literals (several methods share a value; each pinned independently) ──

    private static final String CASE_READ       = "@rbacAuthz.hasPermission('CASE','READ')";
    private static final String CASE_UPDATE     = "@rbacAuthz.hasPermission('CASE','UPDATE')";
    private static final String CASE_DELETE     = "@rbacAuthz.hasPermission('CASE','DELETE')";
    private static final String PDF_CREATE      = "@rbacAuthz.hasPermission('PDF','CREATE')";
    private static final String PDF_READ        = "@rbacAuthz.hasPermission('PDF','READ')";
    private static final String PDF_EXPORT      = "@rbacAuthz.hasPermission('PDF','EXPORT')";
    private static final String FEEDBACK_CREATE = "@rbacAuthz.hasPermission('FEEDBACK','CREATE')";

    /** methodName -> expected @PreAuthorize literal. All 14 gated endpoints. */
    private static final List<String[]> EXPECTED_GATES = List.of(
            new String[]{"getByPatientId",            CASE_READ},
            new String[]{"getProtocolUnderstanding",  CASE_READ},
            new String[]{"getCaseSummary",            CASE_READ},
            new String[]{"addSymptom",                CASE_UPDATE},
            new String[]{"resolveSymptom",            CASE_UPDATE},
            new String[]{"addMedication",             CASE_UPDATE},
            new String[]{"signDeclaration",           CASE_UPDATE},
            new String[]{"closeCase",                 CASE_UPDATE},
            new String[]{"saveProtocolUnderstanding", CASE_UPDATE},
            new String[]{"delete",                    CASE_DELETE},
            new String[]{"generatePdf",               PDF_CREATE},
            new String[]{"getAllPdfs",                PDF_READ},
            new String[]{"getDownloadLink",           PDF_EXPORT},
            new String[]{"resendFeedback",            FEEDBACK_CREATE});

    private static final int EXPECTED_MAPPED = 18;
    private static final int EXPECTED_GATED = 14;

    private static final List<Class<? extends Annotation>> REQUEST_MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class, RequestMapping.class);

    /**
     * Anchored bare-form matcher (identical to {@code LegacyEndpointCutoverSweepTest.BARE_GATE}):
     * the whole {@code @PreAuthorize} value must be exactly
     * {@code @rbacAuthz.hasPermission('<RESOURCE>','<VERB>')} with nothing before or after.
     */
    private static final Pattern BARE_GATE =
            Pattern.compile("^@rbacAuthz\\.hasPermission\\('([A-Z_]+)','([A-Z]+)'\\)$");

    private static boolean isRequestMapped(Method m) {
        return REQUEST_MAPPING_ANNOTATIONS.stream().anyMatch(a -> m.getAnnotation(a) != null);
    }

    /** Resolve a public method by NAME (not parameter types); fail if the name is overloaded. */
    private static String preAuthorizeValueOf(String methodName) {
        Method match = null;
        for (Method m : CaseController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic() || !m.getName().equals(methodName)) {
                continue;
            }
            if (match != null) {
                return fail("method %s on CaseController is ambiguous (overloaded) — cannot pin its @PreAuthorize",
                        methodName);
            }
            match = m;
        }
        if (match == null) {
            return fail("no public method named %s on CaseController", methodName);
        }
        PreAuthorize preAuthorize = match.getAnnotation(PreAuthorize.class);
        if (preAuthorize == null) {
            return fail("method %s on CaseController carries no @PreAuthorize", methodName);
        }
        return preAuthorize.value();
    }

    private static void assertGate(String methodName, String expected) {
        assertThat(preAuthorizeValueOf(methodName))
                .as("%s must be gated by %s", methodName, expected)
                .isEqualTo(expected);
    }

    // ─── Section A — 14 independent literal pins ──────────────────────────

    @Test @DisplayName("getByPatientId is gated CASE:READ")
    void getByPatientId_isGatedByCaseRead() { assertGate("getByPatientId", CASE_READ); }

    @Test @DisplayName("getCaseSummary is gated CASE:READ (summarises the case, not the PDF jobs)")
    void getCaseSummary_isGatedByCaseRead() { assertGate("getCaseSummary", CASE_READ); }

    @Test @DisplayName("addSymptom is gated CASE:UPDATE")
    void addSymptom_isGatedByCaseUpdate() { assertGate("addSymptom", CASE_UPDATE); }

    @Test @DisplayName("resolveSymptom is gated CASE:UPDATE")
    void resolveSymptom_isGatedByCaseUpdate() { assertGate("resolveSymptom", CASE_UPDATE); }

    @Test @DisplayName("addMedication is gated CASE:UPDATE")
    void addMedication_isGatedByCaseUpdate() { assertGate("addMedication", CASE_UPDATE); }

    @Test @DisplayName("signDeclaration is gated CASE:UPDATE")
    void signDeclaration_isGatedByCaseUpdate() { assertGate("signDeclaration", CASE_UPDATE); }

    @Test @DisplayName("closeCase is gated CASE:UPDATE")
    void closeCase_isGatedByCaseUpdate() { assertGate("closeCase", CASE_UPDATE); }

    @Test @DisplayName("delete is gated CASE:DELETE")
    void delete_isGatedByCaseDelete() { assertGate("delete", CASE_DELETE); }

    @Test
    @DisplayName("saveProtocolUnderstanding is gated CASE:UPDATE — CASE, not SESSION_PROTOCOL (D-06): "
            + "this is the healer's protocol understanding for a CASE; the per-session protocol resource "
            + "is scoped to ProtocolController")
    void saveProtocolUnderstanding_isGatedByCaseUpdate() { assertGate("saveProtocolUnderstanding", CASE_UPDATE); }

    @Test
    @DisplayName("getProtocolUnderstanding is gated CASE:READ — CASE, not SESSION_PROTOCOL (D-06)")
    void getProtocolUnderstanding_isGatedByCaseRead() { assertGate("getProtocolUnderstanding", CASE_READ); }

    @Test @DisplayName("generatePdf is gated PDF:CREATE (D-05 PDF triplet)")
    void generatePdf_isGatedByPdfCreate() { assertGate("generatePdf", PDF_CREATE); }

    @Test @DisplayName("getAllPdfs is gated PDF:READ (D-05 PDF triplet)")
    void getAllPdfs_isGatedByPdfRead() { assertGate("getAllPdfs", PDF_READ); }

    @Test @DisplayName("getDownloadLink is gated PDF:EXPORT (D-05) — mints a presigned URL that leaves the control boundary")
    void getDownloadLink_isGatedByPdfExport() { assertGate("getDownloadLink", PDF_EXPORT); }

    @Test
    @DisplayName("resendFeedback is gated FEEDBACK:CREATE — the SOLE FEEDBACK enforcement site in the codebase (D-05 / D-19)")
    void resendFeedback_isGatedByFeedbackCreate() { assertGate("resendFeedback", FEEDBACK_CREATE); }

    // ─── Section B — anti-vacuous over-reach guard ───────────────────────

    @Test
    void everyRequestMappedMethodIsEitherBareGatedOrADeliberatelyOpenException() {
        int inspected = 0;
        int gated = 0;
        List<String> ungated = new ArrayList<>();

        for (Method method : CaseController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || !isRequestMapped(method)) {
                continue;
            }
            inspected++;

            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
            if (preAuthorize == null) {
                ungated.add(method.getName());
                continue;
            }
            gated++;

            String value = preAuthorize.value();
            assertThat(value)
                    .as("method %s's @PreAuthorize must be a bare @rbacAuthz.hasPermission('R','V') expression",
                            method.getName())
                    .matches(BARE_GATE)
                    .doesNotContain("hasRole")
                    .doesNotContain("hasAnyRole");
        }

        assertThat(inspected)
                .as("the sweep must inspect exactly the 18 declared CaseController request-mapped methods — "
                        + "CaseController spans case CRUD + the PDF job pipeline + the patient feedback cycle; "
                        + "a method added or removed here must redden the build rather than silently pass")
                .isEqualTo(EXPECTED_MAPPED);
        assertThat(gated)
                .as("exactly 14 of the 18 CaseController methods carry a bare capability gate (SC1); a dropped "
                        + "gate restores the implicit any-authenticated-healer path over clinical case records")
                .isEqualTo(EXPECTED_GATED);

        TreeSet<String> ungatedSet = new TreeSet<>(ungated);
        assertThat(ungatedSet)
                .as("the only CaseController methods that may stay ungated (SC4 / D-19) are: "
                        + "create — patient self-registration writes a case on a PATIENT-role token (also healer-reachable); "
                        + "getById — the patient health-form reads its case on a PATIENT-role token (also healer-reachable); "
                        + "updateCaseDetails — the patient health-form submits labFindings on a PATIENT-role token (also healer-reachable); "
                        + "getSymptomsForFeedback — the patient feedback form reads case symptoms on a PATIENT-role token. "
                        + "@rbacAuthz.hasPermission(...) returns false for every real caller of these four, so a gate "
                        + "would 403 the live patient self-registration / health-form / feedback flows. Any OTHER "
                        + "ungated method here is an unguarded clinical endpoint. Found: %s", ungated)
                .hasSize(4)
                .containsExactlyInAnyOrder("create", "getById", "updateCaseDetails", "getSymptomsForFeedback");
    }

    // ─── Section C — allow-listed pair resolution + CASE/PDF/FEEDBACK partition ──

    @Test
    void everyGateResolvesIntoTheCasePdfFeedbackPartition() {
        List<String> literals = new ArrayList<>();
        for (String[] entry : EXPECTED_GATES) {
            literals.add(preAuthorizeValueOf(entry[0]));
        }

        assertThat(literals)
                .as("the allow-list check must run over all 14 CaseController gates, never an empty list")
                .hasSize(EXPECTED_GATED);

        Map<Resource, Integer> perResource = new LinkedHashMap<>();
        for (String literal : literals) {
            Matcher matcher = BARE_GATE.matcher(literal);
            assertThat(matcher.matches())
                    .as("%s must be a bare @rbacAuthz.hasPermission('R','V') expression", literal)
                    .isTrue();

            // An unknown literal must FAIL here, never be silently skipped.
            Resource resource = Resource.valueOf(matcher.group(1));
            Operation operation = Operation.valueOf(matcher.group(2));

            assertThat(resource.supports(operation))
                    .as("%s:%s is not an allow-listed (resource, verb) pair (D-10) — RbacAuthz would resolve "
                            + "it to false for every non-SUPADM caller, permanently, with no build error",
                            resource, operation)
                    .isTrue();

            assertThat(resource)
                    .as("%s resolves to %s — no CaseController gate may name SESSION_PROTOCOL; the "
                            + "protocol-understanding pair is CASE:*, not SESSION_PROTOCOL:* (D-06)", literal, resource)
                    .isNotEqualTo(Resource.SESSION_PROTOCOL);

            perResource.merge(resource, 1, Integer::sum);
        }

        assertThat(perResource.keySet())
                .as("CaseController's 14 gates must resolve to exactly the three-resource partition "
                        + "{CASE, PDF, FEEDBACK} (D-05 / D-06)")
                .containsExactlyInAnyOrder(Resource.CASE, Resource.PDF, Resource.FEEDBACK);

        assertThat(perResource.get(Resource.CASE))
                .as("CASE literal count on CaseController must be 10 (3 READ + 6 UPDATE + 1 DELETE)")
                .isEqualTo(10);
        assertThat(perResource.get(Resource.PDF))
                .as("PDF literal count on CaseController must be 3 (CREATE + READ + EXPORT — the D-05 triplet)")
                .isEqualTo(3);
        assertThat(perResource.get(Resource.FEEDBACK))
                .as("FEEDBACK literal count on CaseController must be 1 — resendFeedback is the sole enforcement site")
                .isEqualTo(1);

        // Negative controls: the Section-C loop can tell an allow-listed pair from a rejected one.
        assertThat(Resource.FEEDBACK.supports(Operation.READ))
                .as("FEEDBACK:READ is NOT allow-listed (D-19) — Resource.FEEDBACK permits only {CREATE}. A future "
                        + "FEEDBACK:READ gate on this controller would be un-grantable and 403 forever.")
                .isFalse();
        assertThat(Resource.PDF.supports(Operation.UPDATE))
                .as("PDF:UPDATE is NOT allow-listed — Resource.PDF permits only {READ, CREATE, DELETE, EXPORT, SHARE}. "
                        + "This proves the Section-C positive assertions are not vacuous.")
                .isFalse();
    }
}
