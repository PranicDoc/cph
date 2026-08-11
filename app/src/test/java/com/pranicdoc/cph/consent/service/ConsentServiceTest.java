package com.pranicdoc.cph.consent.service;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.consent.dto.ConsentAcceptRequest;
import com.pranicdoc.cph.consent.dto.ConsentResponse;
import com.pranicdoc.cph.consent.dto.PolicyVersionResponse;
import com.pranicdoc.cph.consent.model.Consent;
import com.pranicdoc.cph.consent.model.ConsentType;
import com.pranicdoc.cph.consent.model.PolicyVersion;
import com.pranicdoc.cph.consent.repository.ConsentRepository;
import com.pranicdoc.cph.consent.repository.ConsentTypeRepository;
import com.pranicdoc.cph.consent.repository.PolicyVersionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    private static final List<String> SEEDED_KEYS = List.of(
            "TERMS_AND_CONDITIONS", "PRIVACY_POLICY", "MEDICAL_DISCLAIMER", "PRACTITIONER_AGREEMENT");

    @Mock private ConsentRepository consentRepository;
    @Mock private PolicyVersionRepository policyVersionRepository;
    @Mock private ConsentTypeRepository consentTypeRepository;
    @Mock private SecurityContextUtil securityContextUtil;
    @Mock private HttpServletRequest httpRequest;
    @Mock private HealerRepository healerRepository;

    @InjectMocks private ConsentService consentService;

    private UUID healerId;

    @BeforeEach
    void setUp() {
        healerId = UUID.randomUUID();
    }

    // --- accept ---

    @Test
    void accept_versionExists_savesServerCapturedConsentAndReturnsResponse() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(policyVersionRepository.findByConsentTypeAndPolicyVersion("PRIVACY_POLICY", 1))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("PRIVACY_POLICY")
                        .policyVersion(1)
                        .mandatory(true)
                        .effectiveDate(Instant.now())
                        .build()));
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.5");
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("Accept-Language")).thenReturn("en-US");
        ArgumentCaptor<Consent> captor = ArgumentCaptor.forClass(Consent.class);
        when(consentRepository.save(captor.capture())).thenAnswer(inv -> {
            Consent c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        ConsentAcceptRequest request = new ConsentAcceptRequest("PRIVACY_POLICY", 1);
        ConsentResponse response = consentService.accept(request, httpRequest);

        Consent saved = captor.getValue();
        assertEquals("HEALER", saved.getSubjectType());
        assertEquals(healerId, saved.getSubjectId());
        assertEquals(healerId, saved.getAcceptedBy());
        assertEquals("PRIVACY_POLICY", saved.getConsentType());
        assertEquals(1, saved.getPolicyVersion());
        assertNotNull(saved.getAcceptedAt());
        assertEquals("CLICKWRAP", saved.getMethod());
        assertNull(saved.getWithdrawnAt());

        assertEquals("PRIVACY_POLICY", response.consentType());
        assertEquals(1, response.policyVersion());
    }

    @Test
    void accept_versionDoesNotExist_throwsAndSavesNothing() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(policyVersionRepository.findByConsentTypeAndPolicyVersion("PRIVACY_POLICY", 99))
                .thenReturn(Optional.empty());

        ConsentAcceptRequest request = new ConsentAcceptRequest("PRIVACY_POLICY", 99);

        assertThrows(ResourceNotFoundException.class, () -> consentService.accept(request, httpRequest));
        verify(consentRepository, never()).save(any());
    }

    @Test
    void accept_metadataSource_userAgentAndLanguageComeFromHeadersNotDto() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(policyVersionRepository.findByConsentTypeAndPolicyVersion("PRIVACY_POLICY", 1))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("PRIVACY_POLICY")
                        .policyVersion(1)
                        .mandatory(true)
                        .effectiveDate(Instant.now())
                        .build()));
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.5");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0 TestBrowser");
        when(httpRequest.getHeader("Accept-Language")).thenReturn("fr-FR");
        ArgumentCaptor<Consent> captor = ArgumentCaptor.forClass(Consent.class);
        when(consentRepository.save(captor.capture())).thenAnswer(inv -> {
            Consent c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        consentService.accept(new ConsentAcceptRequest("PRIVACY_POLICY", 1), httpRequest);

        Consent saved = captor.getValue();
        assertEquals("Mozilla/5.0 TestBrowser", saved.getUserAgent());
        assertEquals("fr-FR", saved.getLanguage());
    }

    @Test
    void accept_auditMetadataRecoverability_savedRowCarriesFullForensicMetadata() {
        // AUDIT-01: the serialized ConsentAcceptRequest payload omits ipAddress/userAgent/
        // language, but the saved Consent row (the AuditLog.entityId join target) carries
        // all three, so the full request metadata stays recoverable.
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(policyVersionRepository.findByConsentTypeAndPolicyVersion("MEDICAL_DISCLAIMER", 1))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("MEDICAL_DISCLAIMER")
                        .policyVersion(1)
                        .mandatory(true)
                        .effectiveDate(Instant.now())
                        .build()));
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7, 10.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("AuditAgent/2.0");
        when(httpRequest.getHeader("Accept-Language")).thenReturn("de-DE");
        ArgumentCaptor<Consent> captor = ArgumentCaptor.forClass(Consent.class);
        when(consentRepository.save(captor.capture())).thenAnswer(inv -> {
            Consent c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        consentService.accept(new ConsentAcceptRequest("MEDICAL_DISCLAIMER", 1), httpRequest);

        Consent saved = captor.getValue();
        assertEquals("10.0.0.1", saved.getIpAddress());
        assertEquals("AuditAgent/2.0", saved.getUserAgent());
        assertEquals("de-DE", saved.getLanguage());
    }

    // --- withdraw ---

    @Test
    void withdraw_ownedByCurrentHealer_setsWithdrawnAtAndSaves() {
        UUID consentId = UUID.randomUUID();
        Consent existing = Consent.builder()
                .subjectType("HEALER")
                .subjectId(healerId)
                .acceptedBy(healerId)
                .consentType("TERMS_AND_CONDITIONS")
                .policyVersion(1)
                .acceptedAt(Instant.now())
                .method("CLICKWRAP")
                .build();
        existing.setId(consentId);
        when(consentRepository.findById(consentId)).thenReturn(Optional.of(existing));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsentResponse response = consentService.withdraw(consentId);

        assertNotNull(existing.getWithdrawnAt());
        assertNotNull(response.withdrawnAt());
        verify(consentRepository).save(existing);
    }

    @Test
    void withdraw_crossSubject_throwsAndNeverSaves() {
        UUID consentId = UUID.randomUUID();
        UUID otherHealerId = UUID.randomUUID();
        Consent existing = Consent.builder()
                .subjectType("HEALER")
                .subjectId(otherHealerId)
                .acceptedBy(otherHealerId)
                .consentType("TERMS_AND_CONDITIONS")
                .policyVersion(1)
                .acceptedAt(Instant.now())
                .method("CLICKWRAP")
                .build();
        existing.setId(consentId);
        when(consentRepository.findById(consentId)).thenReturn(Optional.of(existing));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);

        assertThrows(ResourceNotFoundException.class, () -> consentService.withdraw(consentId));
        verify(consentRepository, never()).save(any());
    }

    @Test
    void withdraw_unknownConsentId_throws() {
        UUID consentId = UUID.randomUUID();
        when(consentRepository.findById(consentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> consentService.withdraw(consentId));
        verify(consentRepository, never()).save(any());
    }

    // --- history ---

    @Test
    void history_returnsAllRecordsInHistoryOrder_includingWithdrawn() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);

        Consent superseded = Consent.builder()
                .subjectType("HEALER").subjectId(healerId).acceptedBy(healerId)
                .consentType("PRIVACY_POLICY").policyVersion(1)
                .acceptedAt(Instant.now().minusSeconds(3600)).method("CLICKWRAP")
                .build();
        superseded.setId(UUID.randomUUID());

        Consent withdrawn = Consent.builder()
                .subjectType("HEALER").subjectId(healerId).acceptedBy(healerId)
                .consentType("TERMS_AND_CONDITIONS").policyVersion(1)
                .acceptedAt(Instant.now().minusSeconds(7200)).withdrawnAt(Instant.now())
                .method("CLICKWRAP")
                .build();
        withdrawn.setId(UUID.randomUUID());

        when(consentRepository.findBySubjectIdOrderByAcceptedAtDesc(healerId))
                .thenReturn(List.of(superseded, withdrawn));

        List<ConsentResponse> result = consentService.history();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.id().equals(superseded.getId())));
        assertTrue(result.stream().anyMatch(r -> r.id().equals(withdrawn.getId()) && r.withdrawnAt() != null));
    }

    // --- activePolicyVersions ---

    @Test
    void activePolicyVersions_returnsLatestPerType_omitsTypesWithNoPolicyVersion() {
        stubActiveTypes(allSeededTypesMandatory());
        lenient().when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("MEDICAL_DISCLAIMER"))
                .thenReturn(Optional.empty());
        lenient().when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRACTITIONER_AGREEMENT"))
                .thenReturn(Optional.empty());
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRIVACY_POLICY"))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("PRIVACY_POLICY").policyVersion(3)
                        .mandatory(true).effectiveDate(Instant.now()).build()));
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("TERMS_AND_CONDITIONS"))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("TERMS_AND_CONDITIONS").policyVersion(1)
                        .mandatory(true).effectiveDate(Instant.now()).build()));

        List<PolicyVersionResponse> result = consentService.activePolicyVersions();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> "PRIVACY_POLICY".equals(r.consentType()) && r.policyVersion() == 3));
        assertTrue(result.stream().anyMatch(r -> "TERMS_AND_CONDITIONS".equals(r.consentType()) && r.policyVersion() == 1));
    }

    // --- outstandingConsents ---

    @Test
    void outstanding_neverAccepted_returnsCurrentMandatoryVersion() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubActiveTypes(allSeededTypesMandatory());
        stubNoCurrentPolicyExcept("PRIVACY_POLICY");
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRIVACY_POLICY"))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("PRIVACY_POLICY").policyVersion(2)
                        .mandatory(true).effectiveDate(Instant.now()).build()));
        when(consentRepository.findTopBySubjectIdAndConsentTypeAndWithdrawnAtIsNullOrderByPolicyVersionDesc(
                healerId, "PRIVACY_POLICY")).thenReturn(Optional.empty());

        List<PolicyVersionResponse> result = consentService.outstandingConsents();

        assertEquals(1, result.size());
        assertEquals("PRIVACY_POLICY", result.get(0).consentType());
        assertEquals(2, result.get(0).policyVersion());
    }

    @Test
    void outstanding_superseded_returnsCurrentVersion() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubActiveTypes(allSeededTypesMandatory());
        stubNoCurrentPolicyExcept("PRIVACY_POLICY");
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRIVACY_POLICY"))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("PRIVACY_POLICY").policyVersion(2)
                        .mandatory(true).effectiveDate(Instant.now()).build()));
        Consent acceptedV1 = Consent.builder()
                .subjectType("HEALER").subjectId(healerId).acceptedBy(healerId)
                .consentType("PRIVACY_POLICY").policyVersion(1)
                .acceptedAt(Instant.now()).method("CLICKWRAP").build();
        acceptedV1.setId(UUID.randomUUID());
        when(consentRepository.findTopBySubjectIdAndConsentTypeAndWithdrawnAtIsNullOrderByPolicyVersionDesc(
                healerId, "PRIVACY_POLICY")).thenReturn(Optional.of(acceptedV1));

        List<PolicyVersionResponse> result = consentService.outstandingConsents();

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).policyVersion());
    }

    @Test
    void outstanding_withdrawnOnly_returnsCurrentVersion() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubActiveTypes(allSeededTypesMandatory());
        stubNoCurrentPolicyExcept("MEDICAL_DISCLAIMER");
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("MEDICAL_DISCLAIMER"))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("MEDICAL_DISCLAIMER").policyVersion(1)
                        .mandatory(true).effectiveDate(Instant.now()).build()));
        // healer accepted v1 then withdrew it -> the WithdrawnAtIsNull query returns empty
        when(consentRepository.findTopBySubjectIdAndConsentTypeAndWithdrawnAtIsNullOrderByPolicyVersionDesc(
                healerId, "MEDICAL_DISCLAIMER")).thenReturn(Optional.empty());

        List<PolicyVersionResponse> result = consentService.outstandingConsents();

        assertEquals(1, result.size());
        assertEquals("MEDICAL_DISCLAIMER", result.get(0).consentType());
    }

    @Test
    void outstanding_satisfied_notInResult() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubActiveTypes(allSeededTypesMandatory());
        stubNoCurrentPolicyExcept("TERMS_AND_CONDITIONS");
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("TERMS_AND_CONDITIONS"))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("TERMS_AND_CONDITIONS").policyVersion(1)
                        .mandatory(true).effectiveDate(Instant.now()).build()));
        Consent acceptedV1 = Consent.builder()
                .subjectType("HEALER").subjectId(healerId).acceptedBy(healerId)
                .consentType("TERMS_AND_CONDITIONS").policyVersion(1)
                .acceptedAt(Instant.now()).method("CLICKWRAP").build();
        acceptedV1.setId(UUID.randomUUID());
        when(consentRepository.findTopBySubjectIdAndConsentTypeAndWithdrawnAtIsNullOrderByPolicyVersionDesc(
                healerId, "TERMS_AND_CONDITIONS")).thenReturn(Optional.of(acceptedV1));

        List<PolicyVersionResponse> result = consentService.outstandingConsents();

        assertTrue(result.stream().noneMatch(r -> "TERMS_AND_CONDITIONS".equals(r.consentType())));
    }

    // RENAMED from outstanding_nonMandatory_notInResult (31.1-02): mandatoriness is now
    // a type-level (ConsentType.mandatory) decision, not a version-level (PolicyVersion.mandatory)
    // one (Pitfall 1) - stubbing reworked to drive the type-level flag; the version-level flag is
    // left mandatory=false too only to prove it is no longer what the gate reads.
    @Test
    void outstanding_typeNotMandatory_notInResult() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubActiveTypes(List.of(
                typeFixture("TERMS_AND_CONDITIONS", true),
                typeFixture("PRIVACY_POLICY", true),
                typeFixture("MEDICAL_DISCLAIMER", true),
                typeFixture("PRACTITIONER_AGREEMENT", false)));
        stubNoCurrentPolicyExcept("PRACTITIONER_AGREEMENT");
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRACTITIONER_AGREEMENT"))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("PRACTITIONER_AGREEMENT").policyVersion(1)
                        .mandatory(false).effectiveDate(Instant.now()).build()));

        List<PolicyVersionResponse> result = consentService.outstandingConsents();

        assertTrue(result.stream().noneMatch(r -> "PRACTITIONER_AGREEMENT".equals(r.consentType())));
    }

    // --- publish ---

    @Test
    void publish_pastedText_noPriorVersion_savesVersion1() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubExistingType("PRIVACY_POLICY");
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRIVACY_POLICY"))
                .thenReturn(Optional.empty());
        ArgumentCaptor<PolicyVersion> captor = ArgumentCaptor.forClass(PolicyVersion.class);
        when(policyVersionRepository.save(captor.capture())).thenAnswer(inv -> {
            PolicyVersion p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PolicyVersionResponse response = consentService.publish(
                "PRIVACY_POLICY", null, "Pasted policy content", true);

        PolicyVersion saved = captor.getValue();
        assertEquals(1, saved.getPolicyVersion());
        assertEquals("Pasted policy content", saved.getContent());
        assertEquals(healerId, saved.getPublisherId());
        assertNotNull(saved.getEffectiveDate());
        assertEquals("PRIVACY_POLICY", response.consentType());
        assertEquals(1, response.policyVersion());
        assertEquals("Pasted policy content", response.content());
        assertEquals(healerId, response.publisherId());
    }

    @Test
    void publish_pastedText_priorVersion3Exists_savesVersion4() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubExistingType("TERMS_AND_CONDITIONS");
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("TERMS_AND_CONDITIONS"))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("TERMS_AND_CONDITIONS").policyVersion(3)
                        .mandatory(true).effectiveDate(Instant.now()).build()));
        ArgumentCaptor<PolicyVersion> captor = ArgumentCaptor.forClass(PolicyVersion.class);
        when(policyVersionRepository.save(captor.capture())).thenAnswer(inv -> {
            PolicyVersion p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        consentService.publish("TERMS_AND_CONDITIONS", null, "Updated T&C", true);

        assertEquals(4, captor.getValue().getPolicyVersion());
    }

    @Test
    void publish_uploadedFile_noPastedText_savesFileBytesDecodedAsUtf8() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubExistingType("MEDICAL_DISCLAIMER");
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("MEDICAL_DISCLAIMER"))
                .thenReturn(Optional.empty());
        String multiByteContent = "Café ☃ policy text"; // accented char + snowman, proves explicit UTF-8
        MockMultipartFile file = new MockMultipartFile("file", "policy.txt", "text/plain",
                multiByteContent.getBytes(StandardCharsets.UTF_8));
        ArgumentCaptor<PolicyVersion> captor = ArgumentCaptor.forClass(PolicyVersion.class);
        when(policyVersionRepository.save(captor.capture())).thenAnswer(inv -> {
            PolicyVersion p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        consentService.publish("MEDICAL_DISCLAIMER", file, null, true);

        assertEquals(multiByteContent, captor.getValue().getContent());
    }

    @Test
    void publish_bothFileAndContent_throwsAndNeverSaves() {
        stubExistingType("PRIVACY_POLICY");
        MockMultipartFile file = new MockMultipartFile("file", "policy.txt", "text/plain",
                "content".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> consentService.publish("PRIVACY_POLICY", file, "pasted text", true));

        assertEquals("Provide either pasted text or a file, not both.", ex.getMessage());
        verify(policyVersionRepository, never()).save(any());
    }

    @Test
    void publish_neitherFileNorContent_throwsAndNeverSaves() {
        stubExistingType("PRIVACY_POLICY");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> consentService.publish("PRIVACY_POLICY", null, null, true));

        assertEquals("Provide either pasted text or a file.", ex.getMessage());
        verify(policyVersionRepository, never()).save(any());
    }

    @Test
    void publish_emptyFileAndBlankContent_treatedAsNeither_throws() {
        stubExistingType("PRIVACY_POLICY");
        MockMultipartFile emptyFile = new MockMultipartFile("file", "policy.txt", "text/plain", new byte[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> consentService.publish("PRIVACY_POLICY", emptyFile, "   ", true));

        assertEquals("Provide either pasted text or a file.", ex.getMessage());
        verify(policyVersionRepository, never()).save(any());
    }

    @Test
    void publish_mandatoryNull_savesMandatoryTrue() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubExistingType("PRACTITIONER_AGREEMENT");
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRACTITIONER_AGREEMENT"))
                .thenReturn(Optional.empty());
        ArgumentCaptor<PolicyVersion> captor = ArgumentCaptor.forClass(PolicyVersion.class);
        when(policyVersionRepository.save(captor.capture())).thenAnswer(inv -> {
            PolicyVersion p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        consentService.publish("PRACTITIONER_AGREEMENT", null, "Agreement text", null);

        assertTrue(captor.getValue().isMandatory());
    }

    @Test
    void publish_mandatoryFalse_savesMandatoryFalse() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubExistingType("PRACTITIONER_AGREEMENT");
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRACTITIONER_AGREEMENT"))
                .thenReturn(Optional.empty());
        ArgumentCaptor<PolicyVersion> captor = ArgumentCaptor.forClass(PolicyVersion.class);
        when(policyVersionRepository.save(captor.capture())).thenAnswer(inv -> {
            PolicyVersion p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        consentService.publish("PRACTITIONER_AGREEMENT", null, "Agreement text", false);

        assertFalse(captor.getValue().isMandatory());
    }

    // --- publish: per-ConsentType traceability (DOC-01/DOC-02/DOC-04/DOC-05) ---

    @Test
    void publish_termsAndConditions_savesCorrectType() {
        assertPublishSavesType("TERMS_AND_CONDITIONS");
    }

    @Test
    void publish_privacyPolicy_savesCorrectType() {
        assertPublishSavesType("PRIVACY_POLICY");
    }

    @Test
    void publish_medicalDisclaimer_savesCorrectType() {
        assertPublishSavesType("MEDICAL_DISCLAIMER");
    }

    @Test
    void publish_practitionerAgreement_savesCorrectType() {
        assertPublishSavesType("PRACTITIONER_AGREEMENT");
    }

    private void assertPublishSavesType(String type) {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubExistingType(type);
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc(type))
                .thenReturn(Optional.empty());
        ArgumentCaptor<PolicyVersion> captor = ArgumentCaptor.forClass(PolicyVersion.class);
        when(policyVersionRepository.save(captor.capture())).thenAnswer(inv -> {
            PolicyVersion p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        consentService.publish(type, null, "Content for " + type, true);

        assertEquals(type, captor.getValue().getConsentType());
        assertEquals("Content for " + type, captor.getValue().getContent());
    }

    @Test
    void publish_saveThrowsDataIntegrityViolation_propagatesUnchanged() {
        // T-31-03: no read-then-write lock is added here; the V25 unique constraint is the
        // atomic backstop and Plan 02's GlobalExceptionHandler maps this to a 409 ProblemDetail
        // at the controller boundary. This test only proves publish() does not swallow it.
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubExistingType("PRIVACY_POLICY");
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRIVACY_POLICY"))
                .thenReturn(Optional.empty());
        when(policyVersionRepository.save(any(PolicyVersion.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(DataIntegrityViolationException.class,
                () -> consentService.publish("PRIVACY_POLICY", null, "content", true));
    }

    // --- publish: annotation contract assertions (D-10/D-11/AUDIT-03) ---
    // Plain Mockito unit tests never run through the Spring AOP proxy, so annotation
    // presence is the only unit-level proof these are wired. End-to-end audit and cache
    // behaviour is verified by Plan 07's checkpoint.

    @Test
    void publish_isAuditedWithPublishPolicyVersionAction() throws NoSuchMethodException {
        Method method = ConsentService.class.getMethod(
                "publish", String.class, MultipartFile.class, String.class, Boolean.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("PUBLISH_POLICY_VERSION", auditable.action());
    }

    @Test
    void publish_evictsPolicyActiveVersionCacheWithMatchingKey() throws NoSuchMethodException {
        Method publishMethod = ConsentService.class.getMethod(
                "publish", String.class, MultipartFile.class, String.class, Boolean.class);
        Method activeVersionsMethod = ConsentService.class.getMethod("activePolicyVersions");

        CacheEvict cacheEvict = publishMethod.getAnnotation(CacheEvict.class);
        Cacheable cacheable = activeVersionsMethod.getAnnotation(Cacheable.class);

        assertNotNull(cacheEvict);
        assertNotNull(cacheable);
        assertTrue(Arrays.asList(cacheEvict.value()).contains("policy.active-version"));
        assertEquals(cacheable.key(), cacheEvict.key());
    }

    @Test
    void publish_cacheEvictDoesNotFlushEntireRegion() throws NoSuchMethodException {
        Method method = ConsentService.class.getMethod(
                "publish", String.class, MultipartFile.class, String.class, Boolean.class);

        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);

        assertFalse(cacheEvict.allEntries());
    }

    /**
     * outstandingConsents() iterates the seeded consent-type keys and calls
     * findTopByConsentTypeOrderByPolicyVersionDesc for every key. Under
     * Mockito's strict stubs, once ANY stubbing exists for that method,
     * an invocation with a different (unstubbed) key throws
     * PotentialStubbingProblem rather than silently returning empty — so
     * every key besides the one under test must be explicitly stubbed
     * to Optional.empty() (no current policy version -> not outstanding).
     */
    private void stubNoCurrentPolicyExcept(String keyUnderTest) {
        for (String key : SEEDED_KEYS) {
            if (!key.equals(keyUnderTest)) {
                lenient().when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc(key))
                        .thenReturn(Optional.empty());
            }
        }
    }

    // --- ConsentType registry fixtures (31.1-02) ---

    /** Builds a ConsentType fixture; active=true and systemDefined=true always, mandatory supplied by the caller. */
    private ConsentType typeFixture(String key, boolean mandatory) {
        return ConsentType.builder()
                .key(key)
                .label(key)
                .mandatory(mandatory)
                .active(true)
                .systemDefined(true)
                .build();
    }

    /** Stubs consentTypeRepository.findAllByActiveTrueOrderByIdAsc() with the supplied registry snapshot. */
    private void stubActiveTypes(List<ConsentType> types) {
        when(consentTypeRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(types);
    }

    /** All 4 seeded keys, mandatory=true - mirrors V27's actual seed data; the common case for existing tests. */
    private List<ConsentType> allSeededTypesMandatory() {
        return SEEDED_KEYS.stream().map(key -> typeFixture(key, true)).toList();
    }

    /** Stubs the publish() existence guard (T-31.1-02) so a publish test reaches its intended assertion instead of 404ing. */
    private void stubExistingType(String key) {
        when(consentTypeRepository.findByKeyAndActiveTrue(key)).thenReturn(Optional.of(typeFixture(key, true)));
    }

    // --- publish: consent-type existence guard (Pitfall 2 / T-31.1-02) ---

    @Test
    void publish_unknownConsentTypeKey_throwsAndNeverSaves() {
        when(consentTypeRepository.findByKeyAndActiveTrue("NOT_A_REAL_KEY")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> consentService.publish("NOT_A_REAL_KEY", null, "text", null));
        verify(policyVersionRepository, never()).save(any());
    }

    @Test
    void publish_deactivatedConsentTypeKey_throwsAndNeverSaves() {
        // D-02: a deactivated type's findByKeyAndActiveTrue also returns empty (the repository
        // method filters on active) - same stubbing shape as the unknown-key case, distinct test
        // documenting the deactivation intent specifically.
        when(consentTypeRepository.findByKeyAndActiveTrue("PRIVACY_POLICY")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> consentService.publish("PRIVACY_POLICY", null, "text", null));
        verify(policyVersionRepository, never()).save(any());
    }

    // --- outstandingConsents: Pitfall 1 mandatory-flag reconciliation ---

    @Test
    void outstandingConsents_typeMandatoryButVersionNotMandatory_isOutstanding() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubActiveTypes(List.of(typeFixture("PRIVACY_POLICY", true)));
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRIVACY_POLICY"))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("PRIVACY_POLICY").policyVersion(1)
                        .mandatory(false).effectiveDate(Instant.now()).build()));
        when(consentRepository.findTopBySubjectIdAndConsentTypeAndWithdrawnAtIsNullOrderByPolicyVersionDesc(
                healerId, "PRIVACY_POLICY")).thenReturn(Optional.empty());

        List<PolicyVersionResponse> result = consentService.outstandingConsents();

        assertEquals(1, result.size());
        assertEquals("PRIVACY_POLICY", result.get(0).consentType());
    }

    @Test
    void outstandingConsents_typeNotMandatoryButVersionMandatory_isNotOutstanding() {
        // Pitfall 1 inversion proof: ConsentType.mandatory=false, PolicyVersion.mandatory=true, a
        // current version exists - this can only pass if the type-level flag is the one being read.
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubActiveTypes(List.of(typeFixture("PRIVACY_POLICY", false)));
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRIVACY_POLICY"))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("PRIVACY_POLICY").policyVersion(2)
                        .mandatory(true).effectiveDate(Instant.now()).build()));

        List<PolicyVersionResponse> result = consentService.outstandingConsents();

        assertTrue(result.stream().noneMatch(r -> "PRIVACY_POLICY".equals(r.consentType())));
    }

    @Test
    void outstandingConsents_deactivatedType_isNotOutstanding() {
        // MEDICAL_DISCLAIMER is deliberately absent from the active-types stub, simulating
        // deactivation - it should never even be queried against policyVersionRepository.
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        stubActiveTypes(List.of(typeFixture("PRIVACY_POLICY", true)));
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRIVACY_POLICY"))
                .thenReturn(Optional.empty());

        List<PolicyVersionResponse> result = consentService.outstandingConsents();

        assertTrue(result.stream().noneMatch(r -> "MEDICAL_DISCLAIMER".equals(r.consentType())));
        verify(policyVersionRepository, never()).findTopByConsentTypeOrderByPolicyVersionDesc("MEDICAL_DISCLAIMER");
    }

    // --- activePolicyVersions: registry-driven iteration ---

    @Test
    void activePolicyVersions_onlyIteratesActiveTypes() {
        stubActiveTypes(List.of(typeFixture("TERMS_AND_CONDITIONS", true), typeFixture("PRIVACY_POLICY", true)));
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("TERMS_AND_CONDITIONS"))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("TERMS_AND_CONDITIONS").policyVersion(1)
                        .mandatory(true).effectiveDate(Instant.now()).build()));
        when(policyVersionRepository.findTopByConsentTypeOrderByPolicyVersionDesc("PRIVACY_POLICY"))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("PRIVACY_POLICY").policyVersion(1)
                        .mandatory(true).effectiveDate(Instant.now()).build()));

        List<PolicyVersionResponse> result = consentService.activePolicyVersions();

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r ->
                "TERMS_AND_CONDITIONS".equals(r.consentType()) || "PRIVACY_POLICY".equals(r.consentType())));
        verify(policyVersionRepository, never()).findTopByConsentTypeOrderByPolicyVersionDesc("MEDICAL_DISCLAIMER");
        verify(policyVersionRepository, never()).findTopByConsentTypeOrderByPolicyVersionDesc("PRACTITIONER_AGREEMENT");
    }
}
