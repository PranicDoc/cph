package com.pranicdoc.cph.consent.service;

import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.consent.dto.ConsentAcceptRequest;
import com.pranicdoc.cph.consent.dto.ConsentResponse;
import com.pranicdoc.cph.consent.model.Consent;
import com.pranicdoc.cph.consent.model.ConsentType;
import com.pranicdoc.cph.consent.model.PolicyVersion;
import com.pranicdoc.cph.consent.repository.ConsentRepository;
import com.pranicdoc.cph.consent.repository.PolicyVersionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock private ConsentRepository consentRepository;
    @Mock private PolicyVersionRepository policyVersionRepository;
    @Mock private SecurityContextUtil securityContextUtil;
    @Mock private HttpServletRequest httpRequest;

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
        when(policyVersionRepository.findByConsentTypeAndPolicyVersion(ConsentType.PRIVACY_POLICY, 1))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType(ConsentType.PRIVACY_POLICY)
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

        ConsentAcceptRequest request = new ConsentAcceptRequest(ConsentType.PRIVACY_POLICY, 1);
        ConsentResponse response = consentService.accept(request, httpRequest);

        Consent saved = captor.getValue();
        assertEquals("HEALER", saved.getSubjectType());
        assertEquals(healerId, saved.getSubjectId());
        assertEquals(healerId, saved.getAcceptedBy());
        assertEquals(ConsentType.PRIVACY_POLICY, saved.getConsentType());
        assertEquals(1, saved.getPolicyVersion());
        assertNotNull(saved.getAcceptedAt());
        assertEquals("CLICKWRAP", saved.getMethod());
        assertNull(saved.getWithdrawnAt());

        assertEquals(ConsentType.PRIVACY_POLICY, response.consentType());
        assertEquals(1, response.policyVersion());
    }

    @Test
    void accept_versionDoesNotExist_throwsAndSavesNothing() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(policyVersionRepository.findByConsentTypeAndPolicyVersion(ConsentType.PRIVACY_POLICY, 99))
                .thenReturn(Optional.empty());

        ConsentAcceptRequest request = new ConsentAcceptRequest(ConsentType.PRIVACY_POLICY, 99);

        assertThrows(ResourceNotFoundException.class, () -> consentService.accept(request, httpRequest));
        verify(consentRepository, never()).save(any());
    }

    @Test
    void accept_metadataSource_userAgentAndLanguageComeFromHeadersNotDto() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(policyVersionRepository.findByConsentTypeAndPolicyVersion(ConsentType.PRIVACY_POLICY, 1))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType(ConsentType.PRIVACY_POLICY)
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

        consentService.accept(new ConsentAcceptRequest(ConsentType.PRIVACY_POLICY, 1), httpRequest);

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
        when(policyVersionRepository.findByConsentTypeAndPolicyVersion(ConsentType.MEDICAL_DISCLAIMER, 1))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType(ConsentType.MEDICAL_DISCLAIMER)
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

        consentService.accept(new ConsentAcceptRequest(ConsentType.MEDICAL_DISCLAIMER, 1), httpRequest);

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
                .consentType(ConsentType.TERMS_AND_CONDITIONS)
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
                .consentType(ConsentType.TERMS_AND_CONDITIONS)
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
}
