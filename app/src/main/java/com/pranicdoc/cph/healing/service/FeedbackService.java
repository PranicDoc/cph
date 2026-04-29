package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.healing.dto.QualitativeFeedbackRequest;
import com.pranicdoc.cph.healing.dto.SymptomFeedbackRequest;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.model.QualitativeFeedback;
import com.pranicdoc.cph.healing.model.SymptomFeedback;
import com.pranicdoc.cph.healing.repository.QualitativeFeedbackRepository;
import com.pranicdoc.cph.healing.repository.SymptomFeedbackRepository;
import lombok.RequiredArgsConstructor;
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

    /**
     * Save symptom feedback for a session (one rating per symptom, scale 0-10).
     */
    @Auditable(action = "SAVE_SYMPTOM_FEEDBACK")
    public void saveSymptomFeedback(UUID sessionId, List<SymptomFeedbackRequest> requests) {
        HealingSession session = sessionService.findOrThrow(sessionId);

        for (SymptomFeedbackRequest req : requests) {
            SymptomFeedback feedback = SymptomFeedback.builder()
                    .healingSession(session)
                    .caseSymptomId(req.caseSymptomId())
                    .rating(req.rating())
                    .notes(req.notes())
                    .build();
            symptomFeedbackRepository.save(feedback);
        }
    }

    /**
     * Save qualitative feedback (interim or final).
     * Enforces: only 1 FINAL per case.
     */
    @Auditable(action = "SAVE_QUALITATIVE_FEEDBACK")
    public void saveQualitativeFeedback(UUID sessionId, QualitativeFeedbackRequest request) {
        HealingSession session = sessionService.findOrThrow(sessionId);

        // Enforce 1 FINAL per case
        if (request.feedbackType() == FeedbackType.FINAL) {
            UUID caseId = session.getHealingCase().getId();
            qualitativeFeedbackRepository
                    .findByHealingSession_HealingCase_IdAndFeedbackType(caseId, FeedbackType.FINAL)
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("A FINAL feedback already exists for this case");
                    });
        }

        QualitativeFeedback feedback = QualitativeFeedback.builder()
                .healingSession(session)
                .feedbackType(request.feedbackType())
                .feedbackText(request.feedbackText())
                .patientRemarks(request.patientRemarks())
                .build();

        qualitativeFeedbackRepository.save(feedback);
    }
}
