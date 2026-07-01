package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.notification.dto.WhatsappCloudMessageDto;
import com.pranicdoc.cph.notification.entity.NotificationOutbox;
import com.pranicdoc.cph.notification.repository.OutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class WhatsappDispatchService {

    private final OutboxRepository outboxRepository;
    private final RestTemplate restTemplate;

    @Value("${app.whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${app.whatsapp.access-token}")
    private String accessToken;

    public WhatsappDispatchService(OutboxRepository outboxRepository, RestTemplate restTemplate) {
        this.outboxRepository = outboxRepository;
        this.restTemplate = restTemplate;
    }

    @Async("whatsappWorkerExecutor")
    @Transactional
    public void executeNotification(NotificationOutbox outbox) {
        try {
            String url = "https://graph.facebook.com/v18.0/" + phoneNumberId + "/messages";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            WhatsappCloudMessageDto dto = WhatsappCloudMessageDto.textMessage(outbox.getPhoneNumber(), outbox.getPayload());
            HttpEntity<WhatsappCloudMessageDto> entity = new HttpEntity<>(dto, headers);
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
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
