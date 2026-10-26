package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.FeedbackSource;
import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.enums.LinkStatus;
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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final CaseService caseService;
    private final SessionRepository sessionRepository;
    private final SecurityContextUtil securityContextUtil;

    /**
     * Save symptom feedback for a session (one rating per symptom, scale 0-10). WhatsApp path:
     * the patient rates the case's latest session, and the feedback is dated with it.
     */
    @Auditable(action = "SAVE_SYMPTOM_FEEDBACK")
    public void saveSymptomFeedback(UUID caseId, List<SymptomFeedbackRequest> requests) {
        HealingSession session = sessionService.findLatestByCaseIdOrThrow(caseId);
        saveSymptoms(session, requests, session.getSessionDate(), FeedbackSource.WHATSAPP);
        feedbackLinkRepository.markUsedBySessionId(caseId, LinkStatus.USED, LinkStatus.ACTIVE);
    }

    /** WhatsApp path: the patient's interim or final comment on the case's latest session. */
    @Auditable(action = "SAVE_QUALITATIVE_FEEDBACK")
    public void saveQualitativeFeedback(UUID caseId, QualitativeFeedbackRequest request) {
        HealingSession session = sessionService.findLatestByCaseIdOrThrow(caseId);
        rejectSecondFinal(caseId, request.feedbackType());
        saveComment(session, request.feedbackType(), request.feedbackText(), request.patientRemarks(),
                session.getSessionDate(), FeedbackSource.WHATSAPP);

        if (request.feedbackType() == FeedbackType.FINAL) {
            feedbackLinkRepository.markUsedBySessionId(caseId, LinkStatus.USED, LinkStatus.ACTIVE);
        }
    }

    /**
     * Feedback the healer enters by hand on the patient's behalf, saved the same way as the
     * WhatsApp path and dated with its session. INTERIM feedback is for the session the healer
     * picked, once per session; FINAL feedback is one comment per case, kept on the latest session.
     * The patient's WhatsApp link stays usable.
     */
    @Auditable(action = "SAVE_MANUAL_FEEDBACK")
    public void saveManualFeedback(UUID caseId, ManualFeedbackRequest request) {
        HealingCase healingCase = caseService.findOrThrow(caseId);
        if (!healingCase.getHealerId().equals(securityContextUtil.getCurrentHealerId())) {
            throw new AccessDeniedException("Case does not belong to you");
        }
        List<SymptomFeedbackRequest> symptoms = request.symptoms() == null ? List.of() : request.symptoms();
        String feedbackText = request.feedbackText() == null ? "" : request.feedbackText().trim();

        if (request.feedbackType() == FeedbackType.FINAL) {
            saveManualFinal(caseId, symptoms, feedbackText);
        } else {
            saveManualInterim(caseId, request.sessionId(), symptoms, feedbackText);
        }
    }

    private void saveManualInterim(UUID caseId, UUID sessionId, List<SymptomFeedbackRequest> symptoms, String text) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Pick the session this feedback is for");
        }
        if (symptoms.isEmpty() && text.isEmpty()) {
            throw new IllegalArgumentException("Rate at least one symptom or add a comment");
        }
        HealingSession session = sessionOfCase(caseId, sessionId);
        if (qualitativeFeedbackRepository.existsByHealingSessionIdAndFeedbackType(session.getId(), FeedbackType.INTERIM)) {
            throw new FeedbackAlreadySubmittedException();
        }
        saveSymptoms(session, symptoms, session.getSessionDate(), FeedbackSource.MANUAL);
        if (!text.isEmpty()) {
            saveComment(session, FeedbackType.INTERIM, text, "", session.getSessionDate(), FeedbackSource.MANUAL);
        }
    }

    private void saveManualFinal(UUID caseId, List<SymptomFeedbackRequest> symptoms, String text) {
        if (!symptoms.isEmpty()) {
            throw new IllegalArgumentException("Final feedback is a comment only");
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Add the final feedback comment");
        }
        rejectSecondFinal(caseId, FeedbackType.FINAL);
        HealingSession latest = sessionService.findLatestByCaseIdOrThrow(caseId);
        saveComment(latest, FeedbackType.FINAL, text, "", latest.getSessionDate(), FeedbackSource.MANUAL);
    }

    // ── Shared by the WhatsApp and manual paths ───────────────────────────────

    private void saveSymptoms(HealingSession session, List<SymptomFeedbackRequest> requests,
                              LocalDate healingDate, FeedbackSource source) {
        try {
            symptomFeedbackRepository.saveAllAndFlush(requests.stream()
                .map(req -> SymptomFeedback.builder()
                    .healingSession(session)
                    .caseSymptomId(req.caseSymptomId())
                    .rating(req.rating())
                    .notes(req.notes())
                    .healingDate(healingDate)
                    .source(source)
                    .build())
                .toList());
        } catch (DataIntegrityViolationException ex) {
            throw new FeedbackAlreadySubmittedException();
        }
    }

    private void saveComment(HealingSession session, FeedbackType type, String text, String patientRemarks,
                             LocalDate healingDate, FeedbackSource source) {
        qualitativeFeedbackRepository.save(QualitativeFeedback.builder()
                .healingSession(session)
                .feedbackType(type)
                .feedbackText(text)
                .patientRemarks(patientRemarks)
                .healingDate(healingDate)
                .source(source)
                .build());
    }

    /** Only one FINAL feedback per case, whichever path it comes through. */
    private void rejectSecondFinal(UUID caseId, FeedbackType type) {
        if (type != FeedbackType.FINAL) return;
        qualitativeFeedbackRepository
            .findByHealingSession_HealingCase_IdAndFeedbackType(caseId, FeedbackType.FINAL)
            .ifPresent(existing -> {
                throw new FeedbackAlreadySubmittedException();
            });
    }

    /** The session the healer picked; it must belong to this case. */
    private HealingSession sessionOfCase(UUID caseId, UUID sessionId) {
        return sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId).stream()
            .filter(s -> sessionId.equals(s.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Session does not belong to this case"));
    }
}
