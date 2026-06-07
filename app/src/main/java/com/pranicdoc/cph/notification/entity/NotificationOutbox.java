package com.pranicdoc.cph.notification.entity;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_outbox", indexes = {
        @Index(name = "idx_status_created", columnList = "status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationOutbox extends BaseEntity {

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    public NotificationOutbox(String phoneNumber, String payload) {
        this.phoneNumber = phoneNumber;
        this.payload = payload;
    }

    public enum NotificationStatus {
        PENDING,
        PROCESSING,
        PROCESSED,
        FAILED,
        DLQ
    }
}