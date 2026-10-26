package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.common.enums.FeedbackSource;
import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.exception.FeedbackAlreadySubmittedException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.dto.ManualFeedbackRequest;
import com.pranicdoc.cph.healing.dto.QualitativeFeedbackRequest;
import com.pranicdoc.cph.healing.dto.SymptomFeedbackRequest;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.model.QualitativeFeedback;
import com.pranicdoc.cph.healing.model.SymptomFeedback;
import com.pranicdoc.cph.healing.repository.QualitativeFeedbackRepository;
import com.pranicdoc.cph.healing.repository.SessionRepository;
import com.pranicdoc.cph.healing.repository.SymptomFeedbackRepository;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the healer's manual feedback entry and the healing date / source that both
 * feedback paths now record.
 */
@ExtendWith(MockitoExtension.class)
class FeedbackServiceManualTest {

    @Mock private SymptomFeedbackRepository symptomFeedbackRepository;
    @Mock private QualitativeFeedbackRepository qualitativeFeedbackRepository;
    @Mock private SessionService sessionService;
    @Mock private FeedbackLinkRepository feedbackLinkRepository;
    @Mock private CaseService caseService;
    @Mock private SessionRepository sessionRepository;
    @Mock private SecurityContextUtil securityContextUtil;

    @InjectMocks private FeedbackService feedbackService;

    private final UUID caseId = UUID.randomUUID();
    private final UUID healerId = UUID.randomUUID();
    private final UUID symptomId = UUID.randomUUID();
    private final HealingSession first = session(1, LocalDate.of(2026, 10, 1));
    private final HealingSession second = session(2, LocalDate.of(2026, 10, 8));

    @BeforeEach
    void setUp() {
        HealingCase healingCase = HealingCase.builder().build();
        healingCase.setHealerId(healerId);
        lenient().when(caseService.findOrThrow(caseId)).thenReturn(healingCase);
        lenient().when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        lenient().when(sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId))
                .thenReturn(List.of(first, second));
    }

    @Test
    void manualInterim_isSavedOnThePickedSession_datedWithIt_markedManual() {
        feedbackService.saveManualFeedback(caseId, interim(first, "Calmer"));

        SymptomFeedback symptom = savedSymptoms().get(0);
        assertSame(first, symptom.getHealingSession());
        assertEquals(LocalDate.of(2026, 10, 1), symptom.getHealingDate());
        assertEquals(FeedbackSource.MANUAL, symptom.getSource());
        assertEquals(7, symptom.getRating());

        ArgumentCaptor<QualitativeFeedback> text = ArgumentCaptor.forClass(QualitativeFeedback.class);
        verify(qualitativeFeedbackRepository).save(text.capture());
        assertEquals("Calmer", text.getValue().getFeedbackText());
        assertEquals(FeedbackType.INTERIM, text.getValue().getFeedbackType());
        assertEquals(FeedbackSource.MANUAL, text.getValue().getSource());
        assertSame(first, text.getValue().getHealingSession());
        // The patient's WhatsApp link is left usable.
        verifyNoInteractions(feedbackLinkRepository);
    }

    @Test
    void manualInterim_withoutAComment_savesOnlyTheRatings() {
        feedbackService.saveManualFeedback(caseId, interim(second, null));

        assertSame(second, savedSymptoms().get(0).getHealingSession());
        verify(qualitativeFeedbackRepository, never()).save(any());
    }

    @Test
    void manualInterim_withoutASession_isRejected() {
        ManualFeedbackRequest noSession = new ManualFeedbackRequest(FeedbackType.INTERIM, null,
                List.of(new SymptomFeedbackRequest(symptomId, 7, "")), "x");

        assertThrows(IllegalArgumentException.class, () -> feedbackService.saveManualFeedback(caseId, noSession));
        verifyNoInteractions(symptomFeedbackRepository);
    }

    @Test
    void manualInterim_withASessionOfAnotherCase_isRejected() {
        ManualFeedbackRequest foreign = new ManualFeedbackRequest(FeedbackType.INTERIM, UUID.randomUUID(),
                List.of(new SymptomFeedbackRequest(symptomId, 7, "")), null);

        assertThrows(IllegalArgumentException.class, () -> feedbackService.saveManualFeedback(caseId, foreign));
        verifyNoInteractions(symptomFeedbackRepository);
    }

    @Test
    void manualInterim_withNothingToSave_isRejected() {
        ManualFeedbackRequest empty = new ManualFeedbackRequest(FeedbackType.INTERIM, first.getId(), List.of(), "  ");

        assertThrows(IllegalArgumentException.class, () -> feedbackService.saveManualFeedback(caseId, empty));
    }

    @Test
    void manualInterim_whenTheSessionAlreadyHasOne_reportsAlreadySubmitted() {
        when(qualitativeFeedbackRepository.existsByHealingSessionIdAndFeedbackType(first.getId(), FeedbackType.INTERIM))
                .thenReturn(true);

        assertThrows(FeedbackAlreadySubmittedException.class, () ->
                feedbackService.saveManualFeedback(caseId, interim(first, "again")));
        verifyNoInteractions(symptomFeedbackRepository);
    }

    @Test
    void manualInterim_forASymptomAlreadyRatedInThatSession_reportsAlreadySubmitted() {
        when(symptomFeedbackRepository.saveAllAndFlush(anyList())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(FeedbackAlreadySubmittedException.class, () ->
                feedbackService.saveManualFeedback(caseId, interim(second, null)));
    }

    @Test
    void manualInterim_onAnotherHealersCase_isDenied() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () ->
                feedbackService.saveManualFeedback(caseId, interim(first, "x")));
        verifyNoInteractions(symptomFeedbackRepository, qualitativeFeedbackRepository);
    }

    @Test
    void manualFinal_isOneCommentOnTheLatestSession() {
        when(sessionService.findLatestByCaseIdOrThrow(caseId)).thenReturn(second);

        feedbackService.saveManualFeedback(caseId,
                new ManualFeedbackRequest(FeedbackType.FINAL, null, List.of(), "  Fully recovered "));

        ArgumentCaptor<QualitativeFeedback> text = ArgumentCaptor.forClass(QualitativeFeedback.class);
        verify(qualitativeFeedbackRepository).save(text.capture());
        assertEquals("Fully recovered", text.getValue().getFeedbackText());
        assertEquals(FeedbackType.FINAL, text.getValue().getFeedbackType());
        assertSame(second, text.getValue().getHealingSession());
        assertEquals(LocalDate.of(2026, 10, 8), text.getValue().getHealingDate());
        verifyNoInteractions(symptomFeedbackRepository);
    }

    @Test
    void manualFinal_whenAFinalAlreadyExists_isRejected() {
        when(qualitativeFeedbackRepository.findByHealingSession_HealingCase_IdAndFeedbackType(caseId, FeedbackType.FINAL))
                .thenReturn(Optional.of(QualitativeFeedback.builder().build()));

        assertThrows(FeedbackAlreadySubmittedException.class, () ->
                feedbackService.saveManualFeedback(caseId, finalFeedback("Done")));
        verify(qualitativeFeedbackRepository, never()).save(any());
    }

    @Test
    void manualFinal_needsAComment_andTakesNoRatings() {
        assertThrows(IllegalArgumentException.class, () -> feedbackService.saveManualFeedback(caseId, finalFeedback(" ")));
        assertThrows(IllegalArgumentException.class, () -> feedbackService.saveManualFeedback(caseId,
                new ManualFeedbackRequest(FeedbackType.FINAL, null,
                        List.of(new SymptomFeedbackRequest(symptomId, 7, "")), "Done")));
        verifyNoInteractions(symptomFeedbackRepository);
    }

    @Test
    void whatsappFeedback_isDatedWithItsSessionAndMarkedWhatsapp() {
        when(sessionService.findLatestByCaseIdOrThrow(caseId)).thenReturn(second);

        feedbackService.saveSymptomFeedback(caseId, List.of(new SymptomFeedbackRequest(symptomId, 4, "")));
        feedbackService.saveQualitativeFeedback(caseId, new QualitativeFeedbackRequest(FeedbackType.FINAL, "Good", ""));

        SymptomFeedback symptom = savedSymptoms().get(0);
        assertEquals(LocalDate.of(2026, 10, 8), symptom.getHealingDate());
        assertEquals(FeedbackSource.WHATSAPP, symptom.getSource());
        ArgumentCaptor<QualitativeFeedback> text = ArgumentCaptor.forClass(QualitativeFeedback.class);
        verify(qualitativeFeedbackRepository).save(text.capture());
        assertEquals(LocalDate.of(2026, 10, 8), text.getValue().getHealingDate());
        assertEquals(FeedbackSource.WHATSAPP, text.getValue().getSource());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ManualFeedbackRequest interim(HealingSession session, String text) {
        return new ManualFeedbackRequest(FeedbackType.INTERIM, session.getId(),
                List.of(new SymptomFeedbackRequest(symptomId, 7, "")), text);
    }

    private ManualFeedbackRequest finalFeedback(String text) {
        return new ManualFeedbackRequest(FeedbackType.FINAL, null, List.of(), text);
    }

    @SuppressWarnings("unchecked")
    private List<SymptomFeedback> savedSymptoms() {
        ArgumentCaptor<List<SymptomFeedback>> captor = ArgumentCaptor.forClass(List.class);
        verify(symptomFeedbackRepository).saveAllAndFlush(captor.capture());
        assertTrue(!captor.getValue().isEmpty());
        return captor.getValue();
    }

    private static HealingSession session(int number, LocalDate date) {
        HealingSession session = HealingSession.builder().sessionNumber(number).sessionDate(date).build();
        session.setId(UUID.randomUUID());
        return session;
    }
}
