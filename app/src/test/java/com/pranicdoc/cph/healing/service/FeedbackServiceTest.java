package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.healing.dto.QualitativeFeedbackRequest;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.model.QualitativeFeedback;
import com.pranicdoc.cph.healing.repository.QualitativeFeedbackRepository;
import com.pranicdoc.cph.healing.repository.SymptomFeedbackRepository;
import com.pranicdoc.cph.healing.service.impl.FeedbackService;
import com.pranicdoc.cph.healing.service.impl.SessionService;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock private SymptomFeedbackRepository symptomFeedbackRepository;
    @Mock private QualitativeFeedbackRepository qualitativeFeedbackRepository;
    @Mock private SessionService sessionService;
    @Mock private FeedbackLinkRepository feedbackLinkRepository;

    @InjectMocks private FeedbackService feedbackService;

    private HealingSession session(int number) {
        HealingSession s = HealingSession.builder().sessionNumber(number).build();
        s.setId(UUID.randomUUID());
        return s;
    }

    @Test
    void firstFinal_insertsNewRowAndMarksLinkUsed() {
        UUID caseId = UUID.randomUUID();
        HealingSession latest = session(1);
        when(sessionService.findLatestByCaseIdOrThrow(caseId)).thenReturn(latest);
        when(qualitativeFeedbackRepository
                .findByHealingSession_HealingCase_IdAndFeedbackType(caseId, FeedbackType.FINAL))
                .thenReturn(Optional.empty());

        feedbackService.saveQualitativeFeedback(caseId,
                new QualitativeFeedbackRequest(FeedbackType.FINAL, "Much better", "Thanks"));

        ArgumentCaptor<QualitativeFeedback> saved = ArgumentCaptor.forClass(QualitativeFeedback.class);
        verify(qualitativeFeedbackRepository).save(saved.capture());
        assertEquals(FeedbackType.FINAL, saved.getValue().getFeedbackType());
        assertEquals("Much better", saved.getValue().getFeedbackText());
        assertSame(latest, saved.getValue().getHealingSession());
        verify(feedbackLinkRepository).markUsedBySessionId(caseId, LinkStatus.USED, LinkStatus.ACTIVE);
    }

    @Test
    void secondFinal_overwritesTextRemarksAndRepointsToLatestSession() {
        UUID caseId = UUID.randomUUID();
        HealingSession oldSession = session(1);
        HealingSession latest = session(2);
        QualitativeFeedback existing = QualitativeFeedback.builder()
                .healingSession(oldSession)
                .feedbackType(FeedbackType.FINAL)
                .feedbackText("Old text")
                .patientRemarks("Old remarks")
                .build();
        when(sessionService.findLatestByCaseIdOrThrow(caseId)).thenReturn(latest);
        when(qualitativeFeedbackRepository
                .findByHealingSession_HealingCase_IdAndFeedbackType(caseId, FeedbackType.FINAL))
                .thenReturn(Optional.of(existing));

        assertDoesNotThrow(() -> feedbackService.saveQualitativeFeedback(caseId,
                new QualitativeFeedbackRequest(FeedbackType.FINAL, "New text", "New remarks")));

        assertEquals("New text", existing.getFeedbackText());
        assertEquals("New remarks", existing.getPatientRemarks());
        assertSame(latest, existing.getHealingSession(),
                "overwrite must re-point the row to the latest session so PDF healing number/date aren't stale");
        verify(qualitativeFeedbackRepository).save(existing);
        verify(qualitativeFeedbackRepository, never()).save(argThat(f -> f != existing));
        verify(feedbackLinkRepository).markUsedBySessionId(caseId, LinkStatus.USED, LinkStatus.ACTIVE);
    }

    @Test
    void interim_afterFinalExists_insertsWithoutTouchingFinalOrLink() {
        UUID caseId = UUID.randomUUID();
        HealingSession latest = session(2);
        when(sessionService.findLatestByCaseIdOrThrow(caseId)).thenReturn(latest);

        feedbackService.saveQualitativeFeedback(caseId,
                new QualitativeFeedbackRequest(FeedbackType.INTERIM, "Midway", null));

        ArgumentCaptor<QualitativeFeedback> saved = ArgumentCaptor.forClass(QualitativeFeedback.class);
        verify(qualitativeFeedbackRepository).save(saved.capture());
        assertEquals(FeedbackType.INTERIM, saved.getValue().getFeedbackType());
        verify(qualitativeFeedbackRepository, never())
                .findByHealingSession_HealingCase_IdAndFeedbackType(any(), any());
        verify(feedbackLinkRepository, never()).markUsedBySessionId(any(), any(), any());
    }
}
