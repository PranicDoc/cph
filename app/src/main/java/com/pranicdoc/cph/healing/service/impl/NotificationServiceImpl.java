package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.common.util.PhoneNumberFormatter;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.service.NotificationService;
import com.pranicdoc.cph.notification.entity.FeedbackLink;
import com.pranicdoc.cph.notification.entity.NotificationOutbox;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import com.pranicdoc.cph.notification.repository.OutboxRepository;
import com.pranicdoc.cph.patient.service.PatientService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {
    private final OutboxRepository outboxRepository;
    private final FeedbackLinkRepository feedbackLinkRepository;
    private final PhoneNumberFormatter phoneNumberFormatter;
    private final PatientService patientService;

    @Value("${app.feedback.url}")
    private String feedbackUrl;

    @Value("${app.feedback.token-expiry-hours:48}")
    private int feedbackLinkExpiryHours;


    @Override
    public void sendFinalFeedbackNotification(HealingSession healingSession) {
        sendFeedbackNotification(healingSession, true);
    }

    @Override
    public void sendFeedbackNotification(HealingSession session, boolean isFinal) {
        HealingCase healingCase = session.getHealingCase();
        String patientNumber = patientService.getById(healingCase.getPatientId()).contact();
        try {
            patientNumber = phoneNumberFormatter.formatToE164(patientNumber);
        } catch (IllegalArgumentException e) {
            return;
        }
        NotificationOutbox outbox = getNotificationOutbox(session, patientNumber, isFinal);
        outboxRepository.save(outbox);
    }

    private NotificationOutbox getNotificationOutbox(HealingSession session, String patientNumber, boolean isFinal) {
        String dateStr = session.getSessionDate() != null ? session.getSessionDate().toString() : "recently";
        Integer sessionNum = session.getSessionNumber();
        HealingCase healingCase = session.getHealingCase();

        FeedbackLink feedbackLink = feedbackLinkRepository.save(
                FeedbackLink.builder()
                        .token(UUID.randomUUID().toString())
                        .caseId(healingCase.getId())
                        // 26-11: FeedbackLink now has a real healerId field (V20) — this is the
                        // CROSS-PLAN NOTE line flipped here, per 26-11's own plan text. Value was
                        // already correct since 26-09 (healingCase.getHealerId() directly); only the
                        // builder's target field name changes.
                        .healerId(healingCase.getHealerId())
                        .status(LinkStatus.ACTIVE)
                        .isFinal(isFinal)
                        .expiresAt(Instant.now().plus(feedbackLinkExpiryHours, ChronoUnit.HOURS))
                        .build()
        );

        String url = feedbackLink.buildUrl(feedbackUrl);
        String message = String.format(
                "Hello, your healing session (Session #%d) on %s is marked as complete. " +
                        "Please take a moment to log your feedback. " +
                        "%s", sessionNum, dateStr, url
        );
        return new NotificationOutbox(patientNumber, message);
    }
}
