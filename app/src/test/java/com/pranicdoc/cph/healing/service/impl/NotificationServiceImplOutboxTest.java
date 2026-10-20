package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.common.util.PhoneNumberFormatter;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.notification.entity.FeedbackLink;
import com.pranicdoc.cph.notification.entity.NotificationOutbox;
import com.pranicdoc.cph.notification.event.OutboxCreatedEvent;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import com.pranicdoc.cph.notification.repository.OutboxRepository;
import com.pranicdoc.cph.patient.dto.PatientResponse;
import com.pranicdoc.cph.patient.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The outbox row is the source of truth; publishing the "send now" event is best-effort. */
class NotificationServiceImplOutboxTest {

    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final FeedbackLinkRepository feedbackLinkRepository = mock(FeedbackLinkRepository.class);
    private final PhoneNumberFormatter phoneNumberFormatter = mock(PhoneNumberFormatter.class);
    private final PatientService patientService = mock(PatientService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private NotificationServiceImpl service;
    private HealingSession session;
    private final UUID outboxId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(outboxRepository, feedbackLinkRepository,
                phoneNumberFormatter, patientService, eventPublisher);
        ReflectionTestUtils.setField(service, "feedbackUrl", "http://localhost/feedback");
        ReflectionTestUtils.setField(service, "feedbackLinkExpiryHours", 48);

        HealingCase healingCase = mock(HealingCase.class);
        when(healingCase.getPatientId()).thenReturn(UUID.randomUUID());
        when(healingCase.getId()).thenReturn(UUID.randomUUID());
        session = mock(HealingSession.class);
        when(session.getHealingCase()).thenReturn(healingCase);
        when(session.getSessionNumber()).thenReturn(1);
        when(session.getSessionDate()).thenReturn(LocalDate.of(2026, 10, 18));

        PatientResponse patient = mock(PatientResponse.class);
        when(patient.contact()).thenReturn("9999999999");
        when(patientService.getById(any())).thenReturn(patient);
        when(phoneNumberFormatter.formatToE164("9999999999")).thenReturn("+919999999999");
        when(feedbackLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, FeedbackLink.class));
        when(outboxRepository.save(any())).thenAnswer(inv -> {
            NotificationOutbox row = inv.getArgument(0);
            row.setId(outboxId);
            return row;
        });
    }

    @Test
    void savesRowThenPublishesSendEvent() {
        service.sendFeedbackNotification(session, false);

        verify(outboxRepository).save(any(NotificationOutbox.class));
        verify(eventPublisher).publishEvent(new OutboxCreatedEvent(outboxId));
    }

    @Test
    void publishFailureDoesNotFailTheCaller_soTheRowCommitsAndTheSweeperSendsIt() {
        doThrow(new IllegalStateException("no synchronization")).when(eventPublisher).publishEvent(any(Object.class));

        assertThatCode(() -> service.sendFeedbackNotification(session, false)).doesNotThrowAnyException();
        verify(outboxRepository).save(any(NotificationOutbox.class));
    }
}
