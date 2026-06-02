package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.notification.dto.WhatsappMessageDto;
import com.pranicdoc.cph.notification.entity.NotificationOutbox;
import com.pranicdoc.cph.notification.repository.OutboxRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class WhatsappDispatchService {

    private final OutboxRepository outboxRepository;
    private final RestTemplate restTemplate;
    private static final String WHATSAPP_API_URL = "https://api.whatsapp.com/v1/messages";

    public WhatsappDispatchService(OutboxRepository outboxRepository, RestTemplate restTemplate) {
        this.outboxRepository = outboxRepository;
        this.restTemplate = restTemplate;
    }

    @Async("whatsappWorkerExecutor")
    @Transactional
    public void executeNotification(NotificationOutbox outbox) {
        try {
            WhatsappMessageDto payload = new WhatsappMessageDto(outbox.getPhoneNumber(), outbox.getPayload());
            restTemplate.postForEntity(WHATSAPP_API_URL, payload, String.class);
            outbox.setStatus(NotificationOutbox.NotificationStatus.PROCESSED);
        } catch (Exception e) {
            outbox.setRetryCount(outbox.getRetryCount() + 1);
            if (outbox.getRetryCount() >= 3) {
                outbox.setStatus(NotificationOutbox.NotificationStatus.DLQ);
            } else {
                outbox.setStatus(NotificationOutbox.NotificationStatus.FAILED);
            }
        } finally {
            outboxRepository.save(outbox);
        }
    }
}