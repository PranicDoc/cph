package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.common.exception.FeedbackAlreadySubmittedException;
import com.pranicdoc.cph.healing.dto.QualitativeFeedbackRequest;
import com.pranicdoc.cph.healing.dto.SymptomFeedbackRequest;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.model.QualitativeFeedback;
import com.pranicdoc.cph.healing.model.SymptomFeedback;
import com.pranicdoc.cph.healing.repository.QualitativeFeedbackRepository;
import com.pranicdoc.cph.healing.repository.SymptomFeedbackRepository;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FeedbackService {

    private final SymptomFeedbackRepository symptomFeedbackRepository;
    private final QualitativeFeedbackRepository qualitativeFeedbackRepository;
    private final SessionService sessionService;
    private final FeedbackLinkRepository feedbackLinkRepository;

    /**
     * Save symptom feedback for a session (one rating per symptom, scale 0-10).
     */
    @Auditable(action = "SAVE_SYMPTOM_FEEDBACK")
    public void saveSymptomFeedback(UUID caseId, List<SymptomFeedbackRequest> requests) {
        HealingSession session = sessionService.findLatestByCaseIdOrThrow(caseId);
        try {
            List<SymptomFeedback> feedbacks = requests.stream()
                .map(req -> SymptomFeedback.builder()
                    .healingSession(session)
                    .caseSymptomId(req.caseSymptomId())
                    .rating(req.rating())
                    .notes(req.notes())
                    .build())
                .toList();
            symptomFeedbackRepository.saveAllAndFlush(feedbacks);
            feedbackLinkRepository.markUsedBySessionId(caseId, LinkStatus.USED, LinkStatus.ACTIVE);
        } catch (DataIntegrityViolationException ex) {
            throw new FeedbackAlreadySubmittedException();
        }
    }

    @Auditable(action = "SAVE_QUALITATIVE_FEEDBACK")
    public void saveQualitativeFeedback(UUID caseId, QualitativeFeedbackRequest request) {
        HealingSession session = sessionService.findLatestByCaseIdOrThrow(caseId);

        if (request.feedbackType() == FeedbackType.FINAL) {
            qualitativeFeedbackRepository
                .findByHealingSession_HealingCase_IdAndFeedbackType(caseId, FeedbackType.FINAL)
                .ifPresent(existing -> {
                    throw new FeedbackAlreadySubmittedException();
                });
        }

        QualitativeFeedback feedback = QualitativeFeedback.builder()
                .healingSession(session)
                .feedbackType(request.feedbackType())
                .feedbackText(request.feedbackText())
                .patientRemarks(request.patientRemarks())
                .build();

        qualitativeFeedbackRepository.save(feedback);

        if (request.feedbackType() == FeedbackType.FINAL) {
            feedbackLinkRepository.markUsedBySessionId(caseId, LinkStatus.USED, LinkStatus.ACTIVE);
        }
    }
}
