package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.enums.FeedbackSource;
import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.common.exception.CaseLockedException;
import com.pranicdoc.cph.common.exception.FeedbackAlreadySubmittedException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.validation.TextLimits;
import com.pranicdoc.cph.healing.dto.CaseFeedbackResponse;
import com.pranicdoc.cph.healing.dto.ManualFeedbackRequest;
import com.pranicdoc.cph.healing.dto.QualitativeFeedbackRequest;
import com.pranicdoc.cph.healing.dto.SymptomFeedbackRequest;
import com.pranicdoc.cph.healing.model.DeclarationDates;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        rejectSecond(caseId, session, request.feedbackType());
        rejectOverLimit(request.feedbackType(), request.feedbackText());
        saveComment(session, request.feedbackType(), request.feedbackText(), request.patientRemarks(),
                session.getSessionDate(), FeedbackSource.WHATSAPP);

        if (request.feedbackType() == FeedbackType.FINAL) {
            feedbackLinkRepository.markUsedBySessionId(caseId, LinkStatus.USED, LinkStatus.ACTIVE);
        }
    }

    /**
     * Feedback the healer enters by hand on the patient's behalf, saved the same way as the
     * WhatsApp path and dated with its session (a FINAL takes the date the healer chose, default the latest session's). INTERIM feedback is for the session the healer
     * picked, one per session; FINAL feedback is one comment per case, kept on the latest session.
     * Saving again updates what is there, whichever path first recorded it (its source stays).
     * The patient's WhatsApp link stays usable.
     */
    @Auditable(action = "SAVE_MANUAL_FEEDBACK")
    public void saveManualFeedback(UUID caseId, ManualFeedbackRequest request) {
        HealingCase healingCase = ownedCase(caseId);
        if (healingCase.getStatus() == CaseStatus.LOCKED) {
            throw new CaseLockedException();
        }
        List<SymptomFeedbackRequest> symptoms = request.symptoms() == null ? List.of() : request.symptoms();
        String feedbackText = request.feedbackText() == null ? "" : request.feedbackText().trim();
        rejectOverLimit(request.feedbackType(), feedbackText);

        if (request.feedbackType() == FeedbackType.FINAL) {
            saveManualFinal(caseId, symptoms, feedbackText, request.finalFeedbackDate());
        } else {
            saveManualInterim(caseId, request.sessionId(), symptoms, feedbackText);
        }
    }

    /** What is recorded for the case, so the healer's form can show it for editing. */
    @Transactional(readOnly = true)
    public CaseFeedbackResponse getManualFeedback(UUID caseId) {
        ownedCase(caseId);
        List<CaseFeedbackResponse.SessionFeedback> interims = new ArrayList<>();
        for (HealingSession session : sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId)) {
            List<CaseFeedbackResponse.Rating> ratings = symptomFeedbackRepository.findByHealingSessionId(session.getId())
                    .stream()
                    .map(f -> new CaseFeedbackResponse.Rating(f.getCaseSymptomId(), f.getRating()))
                    .toList();
            String text = qualitativeFeedbackRepository
                    .findByHealingSessionIdAndFeedbackTypeOrderByCreatedAtDesc(session.getId(), FeedbackType.INTERIM)
                    .stream().findFirst().map(QualitativeFeedback::getFeedbackText).orElse(null);
            if (text != null || !ratings.isEmpty()) {
                interims.add(new CaseFeedbackResponse.SessionFeedback(session.getId(), text, ratings));
            }
        }
        Optional<QualitativeFeedback> finalFeedback = newestFinal(caseId);
        return new CaseFeedbackResponse(interims,
                finalFeedback.map(QualitativeFeedback::getFeedbackText).orElse(null),
                finalFeedback.map(QualitativeFeedback::getHealingDate).orElse(null));
    }

    private HealingCase ownedCase(UUID caseId) {
        HealingCase healingCase = caseService.findOrThrow(caseId);
        if (!healingCase.getHealerId().equals(securityContextUtil.getCurrentHealerId())) {
            throw new AccessDeniedException("Case does not belong to you");
        }
        return healingCase;
    }

    private void saveManualInterim(UUID caseId, UUID sessionId, List<SymptomFeedbackRequest> symptoms, String text) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Pick the session this feedback is for");
        }
        if (symptoms.isEmpty() && text.isEmpty()) {
            throw new IllegalArgumentException("Rate at least one symptom or add a comment");
        }
        HealingSession session = sessionOfCase(caseId, sessionId);
        saveOrUpdateSymptoms(session, symptoms);

        Optional<QualitativeFeedback> existing = qualitativeFeedbackRepository
                .findByHealingSessionIdAndFeedbackTypeOrderByCreatedAtDesc(session.getId(), FeedbackType.INTERIM)
                .stream().findFirst();
        if (existing.isPresent()) {
            existing.get().setFeedbackText(text);
            qualitativeFeedbackRepository.save(existing.get());
        } else if (!text.isEmpty()) {
            saveComment(session, FeedbackType.INTERIM, text, "", session.getSessionDate(), FeedbackSource.MANUAL);
        }
    }

    private void saveManualFinal(UUID caseId, List<SymptomFeedbackRequest> symptoms, String text, LocalDate date) {
        if (!symptoms.isEmpty()) {
            throw new IllegalArgumentException("Final feedback is a comment only");
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Add the final feedback comment");
        }
        if (date != null && date.isAfter(DeclarationDates.today())) {
            throw new IllegalArgumentException("The final feedback date cannot be in the future");
        }
        Optional<QualitativeFeedback> existing = newestFinal(caseId);
        if (existing.isPresent()) {
            existing.get().setFeedbackText(text);
            if (date != null) existing.get().setHealingDate(date);
            qualitativeFeedbackRepository.save(existing.get());
            return;
        }
        HealingSession latest = sessionService.findLatestByCaseIdOrThrow(caseId);
        saveComment(latest, FeedbackType.FINAL, text, "", date != null ? date : latest.getSessionDate(), FeedbackSource.MANUAL);
    }

    /** Updates the ratings the session already has for these symptoms and adds the rest. */
    private void saveOrUpdateSymptoms(HealingSession session, List<SymptomFeedbackRequest> requests) {
        Map<UUID, SymptomFeedback> existing = new HashMap<>();
        symptomFeedbackRepository.findByHealingSessionId(session.getId())
                .forEach(f -> existing.put(f.getCaseSymptomId(), f));
        List<SymptomFeedback> toSave = requests.stream().map(req -> {
            SymptomFeedback feedback = existing.get(req.caseSymptomId());
            if (feedback == null) {
                return newSymptomFeedback(session, req, session.getSessionDate(), FeedbackSource.MANUAL);
            }
            feedback.setRating(req.rating());
            feedback.setNotes(req.notes());
            return feedback;
        }).toList();
        flushSymptoms(toSave);
    }

    private Optional<QualitativeFeedback> newestFinal(UUID caseId) {
        return qualitativeFeedbackRepository
                .findByHealingSession_HealingCase_IdAndFeedbackTypeOrderByCreatedAtDesc(caseId, FeedbackType.FINAL)
                .stream().findFirst();
    }

    // ── Shared by the WhatsApp and manual paths ───────────────────────────────

    private void saveSymptoms(HealingSession session, List<SymptomFeedbackRequest> requests,
                              LocalDate healingDate, FeedbackSource source) {
        flushSymptoms(requests.stream()
                .map(req -> newSymptomFeedback(session, req, healingDate, source))
                .toList());
    }

    private static SymptomFeedback newSymptomFeedback(HealingSession session, SymptomFeedbackRequest req,
                                                      LocalDate healingDate, FeedbackSource source) {
        return SymptomFeedback.builder()
                .healingSession(session)
                .caseSymptomId(req.caseSymptomId())
                .rating(req.rating())
                .notes(req.notes())
                .healingDate(healingDate)
                .source(source)
                .build();
    }

    private void flushSymptoms(List<SymptomFeedback> feedback) {
        try {
            symptomFeedbackRepository.saveAllAndFlush(feedback);
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

    /** The patient's path only adds: one FINAL per case and one INTERIM per session. The healer's path edits. */
    private void rejectSecond(UUID caseId, HealingSession session, FeedbackType type) {
        boolean exists = type == FeedbackType.FINAL
                ? newestFinal(caseId).isPresent()
                : qualitativeFeedbackRepository.existsByHealingSessionIdAndFeedbackType(session.getId(), type);
        if (exists) {
            throw new FeedbackAlreadySubmittedException();
        }
    }

    /** The interim box in the PDF is smaller than the final one, so the limit depends on the type. */
    private static void rejectOverLimit(FeedbackType type, String text) {
        int limit = type == FeedbackType.FINAL ? TextLimits.FINAL_FEEDBACK : TextLimits.INTERIM_FEEDBACK;
        if (text != null && text.length() > limit) {
            throw new IllegalArgumentException("Feedback can be at most " + limit + " characters");
        }
    }

    /** The session the healer picked; it must belong to this case. */
    private HealingSession sessionOfCase(UUID caseId, UUID sessionId) {
        return sessionRepository.findByHealingCaseIdOrderBySessionNumberAsc(caseId).stream()
            .filter(s -> sessionId.equals(s.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Session does not belong to this case"));
    }
}
