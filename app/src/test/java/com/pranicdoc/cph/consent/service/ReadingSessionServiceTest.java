package com.pranicdoc.cph.consent.service;

import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.consent.dto.ReadingSessionCloseRequest;
import com.pranicdoc.cph.consent.dto.ReadingSessionOpenRequest;
import com.pranicdoc.cph.consent.dto.ReadingSessionResponse;
import com.pranicdoc.cph.consent.model.PolicyVersion;
import com.pranicdoc.cph.consent.model.ReadingSession;
import com.pranicdoc.cph.consent.repository.PolicyVersionRepository;
import com.pranicdoc.cph.consent.repository.ReadingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReadingSessionServiceTest {

    @Mock private ReadingSessionRepository readingSessionRepository;
    @Mock private PolicyVersionRepository policyVersionRepository;
    @Mock private SecurityContextUtil securityContextUtil;

    @InjectMocks private ReadingSessionService readingSessionService;

    private UUID healerId;
    private UUID otherHealerId;

    @BeforeEach
    void setUp() {
        healerId = UUID.randomUUID();
        otherHealerId = UUID.randomUUID();
    }

    // --- open ---

    @Test
    void open_versionExists_savesServerCapturedOpenedAtAndSubjectId() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(policyVersionRepository.findByConsentTypeAndPolicyVersion("PRIVACY_POLICY", 1))
                .thenReturn(Optional.of(PolicyVersion.builder()
                        .consentType("PRIVACY_POLICY")
                        .policyVersion(1)
                        .mandatory(true)
                        .effectiveDate(Instant.now())
                        .build()));
        ArgumentCaptor<ReadingSession> captor = ArgumentCaptor.forClass(ReadingSession.class);
        when(readingSessionRepository.save(captor.capture())).thenAnswer(inv -> {
            ReadingSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        ReadingSessionOpenRequest request = new ReadingSessionOpenRequest("PRIVACY_POLICY", 1);
        readingSessionService.open(request);

        ReadingSession saved = captor.getValue();
        assertEquals(healerId, saved.getSubjectId());
        assertNotNull(saved.getOpenedAt());
        assertNull(saved.getClosedAt());
        assertNull(saved.getDurationSeconds());
        assertFalse(saved.isScrollCompleted());
        assertEquals("PRIVACY_POLICY", saved.getConsentType());
        assertEquals(1, saved.getPolicyVersion());
    }

    @Test
    void open_versionMissing_throwsResourceNotFoundAndNeverSaves() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(policyVersionRepository.findByConsentTypeAndPolicyVersion("PRIVACY_POLICY", 99))
                .thenReturn(Optional.empty());

        ReadingSessionOpenRequest request = new ReadingSessionOpenRequest("PRIVACY_POLICY", 99);

        assertThrows(ResourceNotFoundException.class, () -> readingSessionService.open(request));
        verify(readingSessionRepository, never()).save(any());
    }

    // --- close ---

    @Test
    void close_ownSession_setsClosedAtAndServerComputedDuration() {
        UUID id = UUID.randomUUID();
        ReadingSession existing = ReadingSession.builder()
                .subjectId(healerId)
                .consentType("PRIVACY_POLICY")
                .policyVersion(1)
                .openedAt(Instant.now().minusSeconds(42))
                .scrollCompleted(false)
                .build();
        existing.setId(id);
        when(readingSessionRepository.findById(id)).thenReturn(Optional.of(existing));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        ArgumentCaptor<ReadingSession> captor = ArgumentCaptor.forClass(ReadingSession.class);
        when(readingSessionRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        readingSessionService.close(id, new ReadingSessionCloseRequest(true));

        ReadingSession saved = captor.getValue();
        assertNotNull(saved.getClosedAt());
        assertTrue(saved.getDurationSeconds() >= 42);
        assertTrue(saved.isScrollCompleted());
    }

    @Test
    void close_otherHealersSession_throwsResourceNotFoundAndNeverSaves() {
        UUID id = UUID.randomUUID();
        ReadingSession existing = ReadingSession.builder()
                .subjectId(otherHealerId)
                .consentType("PRIVACY_POLICY")
                .policyVersion(1)
                .openedAt(Instant.now())
                .scrollCompleted(false)
                .build();
        existing.setId(id);
        when(readingSessionRepository.findById(id)).thenReturn(Optional.of(existing));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);

        assertThrows(ResourceNotFoundException.class,
                () -> readingSessionService.close(id, new ReadingSessionCloseRequest(true)));
        verify(readingSessionRepository, never()).save(any());
    }

    @Test
    void close_alreadyClosed_isIdempotentAndNeverSaves() {
        UUID id = UUID.randomUUID();
        Instant closedAt = Instant.now().minusSeconds(5);
        ReadingSession existing = ReadingSession.builder()
                .subjectId(healerId)
                .consentType("PRIVACY_POLICY")
                .policyVersion(1)
                .openedAt(Instant.now().minusSeconds(15))
                .closedAt(closedAt)
                .durationSeconds(10L)
                .scrollCompleted(false)
                .build();
        existing.setId(id);
        when(readingSessionRepository.findById(id)).thenReturn(Optional.of(existing));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);

        ReadingSessionResponse response = readingSessionService.close(id, new ReadingSessionCloseRequest(true));

        assertEquals(closedAt, response.closedAt());
        assertEquals(10L, response.durationSeconds());
        verify(readingSessionRepository, never()).save(any());
    }

    @Test
    void close_nullRequestBody_defaultsScrollCompletedToFalse() {
        UUID id = UUID.randomUUID();
        ReadingSession existing = ReadingSession.builder()
                .subjectId(healerId)
                .consentType("PRIVACY_POLICY")
                .policyVersion(1)
                .openedAt(Instant.now().minusSeconds(5))
                .scrollCompleted(false)
                .build();
        existing.setId(id);
        when(readingSessionRepository.findById(id)).thenReturn(Optional.of(existing));
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        ArgumentCaptor<ReadingSession> captor = ArgumentCaptor.forClass(ReadingSession.class);
        when(readingSessionRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> readingSessionService.close(id, null));

        assertFalse(captor.getValue().isScrollCompleted());
    }
}
