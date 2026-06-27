package com.pranicdoc.cph.notification.entity;

import com.pranicdoc.cph.common.enums.LinkStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feedback_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackLink {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String token;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LinkStatus status = LinkStatus.ACTIVE;

    @Column(name = "is_final")
    @Builder.Default
    private boolean isFinal = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public String buildUrl(String baseUrl) {
        return baseUrl + "?token=" + token + "&caseId=" + caseId + "&final=" + isFinal;
    }
}
