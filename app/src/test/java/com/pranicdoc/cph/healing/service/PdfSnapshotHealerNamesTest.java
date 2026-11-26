package com.pranicdoc.cph.healing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantContext;
import com.pranicdoc.cph.healing.model.CaseClosure;
import com.pranicdoc.cph.healing.model.Declaration;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.PdfJobRepository;
import com.pranicdoc.cph.healing.service.impl.CaseService;
import com.pranicdoc.cph.patient.dto.PatientCreateRequest;
import com.pranicdoc.cph.patient.model.Patient;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.cph.support.TestRedisConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A (re)generated PDF prints the healers' current names, read from the healer table, while the
 * signatures stored on the case stay as recorded.
 */
@SpringBootTest
@Import(TestRedisConfig.class)
@ActiveProfiles("test")
@Transactional
class PdfSnapshotHealerNamesTest {

    @MockBean private SecurityContextUtil security;
    @MockBean private NotificationService notificationService;
    @MockBean private HealerCacheService healerCacheService;

    @Autowired private CaseService caseService;
    @Autowired private HealerRepository healers;
    @Autowired private PatientRepository patients;
    @Autowired private CaseRepository cases;
    @Autowired private PdfJobRepository pdfJobs;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager em;

    private UUID caseHealer;
    private UUID closer;

    @BeforeEach
    void seed() {
        // The cache still holds the old name: the payload must not come from it.
        when(healerCacheService.getName(any())).thenReturn("Cached Old Name");
        caseHealer = healer("New Name", "case@x.com");
        closer = healer("New Closer Name", "closer@x.com");
        TenantContext.set(caseHealer);
        when(security.getCurrentRole()).thenReturn("HEALER");
        when(security.getCurrentHealerId()).thenReturn(caseHealer);
        when(security.getCurrentHealerIdOrNull()).thenReturn(caseHealer);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UUID healer(String name, String email) {
        return healers.saveAndFlush(Healer.builder().fullName(name).email(email).role(Role.HEALER).build()).getId();
    }

    private static Declaration declaration() {
        return Declaration.builder().patientConsent(true).healerDeclaration(true)
                .patientSignature("Asha Rao").healerSignature("Old Name").signedAt(Instant.now()).build();
    }

    private HealingCase newCase(UUID healerId, Declaration declaration) {
        Patient patient = patients.save(Patient.create(new PatientCreateRequest("Asha", "Rao", null, null, null,
                null, "9876543210", null, null, null, null, null, null, null), caseHealer));
        HealingCase healingCase = HealingCase.create(patient.getId(), healerId, declaration());
        // Cases from before consent existed have no declaration; create() no longer allows that.
        healingCase.setDeclaration(declaration);
        if (declaration != null) {
            declaration.setHealingCase(healingCase);
        }
        return cases.saveAndFlush(healingCase);
    }

    private HealingCase close(HealingCase healingCase, UUID closedBy) {
        healingCase.setClosure(CaseClosure.builder().healingCase(healingCase).closureDate(LocalDate.now())
                .healingCount(1).isHealingContinued(false).healerSignature("Old Closer Name")
                .closedBy(closedBy).build());
        return cases.saveAndFlush(healingCase);
    }

    private JsonNode payload(HealingCase healingCase) throws Exception {
        em.flush();
        caseService.buildAndEnqueuePdfJob(healingCase.getId());
        return objectMapper.readTree(pdfJobs.findTopByHealingCaseIdOrderByCreatedAtDesc(healingCase.getId())
                .orElseThrow().getHealingCaseData());
    }

    @Test
    void regeneratedPdfUsesCurrentHealerNames() throws Exception {
        HealingCase healingCase = close(newCase(caseHealer, declaration()), caseHealer);

        JsonNode data = payload(healingCase);
        em.flush();
        em.clear();

        assertThat(data.get("healerSign").asText()).isEqualTo("New Name");
        assertThat(data.get("caseClosure").get("healerSignature").asText()).isEqualTo("New Name");
        HealingCase stored = cases.findById(healingCase.getId()).orElseThrow();
        assertThat(stored.getDeclaration().getHealerSignature()).isEqualTo("Old Name");
        assertThat(stored.getClosure().getHealerSignature()).isEqualTo("Old Closer Name");
    }

    @Test
    void closureNameFollowsWhoeverClosedTheCase() throws Exception {
        JsonNode data = payload(close(newCase(caseHealer, declaration()), closer));

        assertThat(data.get("healerSign").asText()).isEqualTo("New Name");
        assertThat(data.get("caseClosure").get("healerSignature").asText()).isEqualTo("New Closer Name");
    }

    @Test
    void storedNameKeptWhenClosedByIsUnknown() throws Exception {
        JsonNode data = payload(close(newCase(caseHealer, declaration()), null));

        assertThat(data.get("caseClosure").get("healerSignature").asText()).isEqualTo("Old Closer Name");
    }

    @Test
    void storedNamesKeptWhenHealersCannotBeResolved() throws Exception {
        UUID blankName = healer(" ", "blank@x.com");

        JsonNode data = payload(close(newCase(blankName, declaration()), UUID.randomUUID()));

        assertThat(data.get("healerSign").asText()).isEqualTo("Old Name");
        assertThat(data.get("caseClosure").get("healerSignature").asText()).isEqualTo("Old Closer Name");
    }

    @Test
    void noDeclarationStillSendsNoHealerSignAndOpenCaseHasNoClosure() throws Exception {
        JsonNode data = payload(newCase(caseHealer, null));

        assertThat(data.get("healerSign").isNull()).isTrue();
        assertThat(data.hasNonNull("caseClosure")).isFalse();
    }
}
