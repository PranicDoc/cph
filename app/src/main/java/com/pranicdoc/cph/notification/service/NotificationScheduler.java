package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.notification.entity.NotificationOutbox;
import com.pranicdoc.cph.notification.repository.OutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class NotificationScheduler {

    private final OutboxRepository outboxRepository;
    private final WhatsappDispatchService dispatchService;

    public NotificationScheduler(OutboxRepository outboxRepository, WhatsappDispatchService dispatchService) {
        this.outboxRepository = outboxRepository;
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void pollAndFanOut() {
        List<NotificationOutbox> actionableRecords = outboxRepository.findPendingJobsForWorker(50);
        if (actionableRecords.isEmpty()) {
            return;
        }
        for (NotificationOutbox outbox : actionableRecords) {
            outbox.setStatus(NotificationOutbox.NotificationStatus.PROCESSING);
        }
        outboxRepository.saveAll(actionableRecords);
        for (NotificationOutbox outbox : actionableRecords) {
            dispatchService.executeNotification(outbox);
        }
    }
}