package com.pranicdoc.cph.auth.model;

import com.pranicdoc.cph.common.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Standalone (non-BaseEntity/TenantEntity) invite-link entity for admin healer
 * onboarding (ADMIN-01). Deliberately does NOT extend BaseEntity/TenantEntity —
 * those carry a class-level @Audited (Hibernate Envers) that would silently
 * require an unplanned healer_invite_links_aud table. Invites are ephemeral
 * tokens, not field-audited domain records — mirrors FeedbackLink/RegistrationLink.
 *
 * Unlike FeedbackLink (token-as-@Id), this entity uses a surrogate id PK plus a
 * separate unique token column: admin resend/revoke reference invites by the
 * non-secret surrogate id, while the public accept flow looks up by token.
 */
@Entity
@Table(name = "healer_invite_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealerInviteLink {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(length = 36, nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "invited_by_healer_id", nullable = false)
    private UUID invitedByHealerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum Status { PENDING, ACCEPTED, REVOKED }
}
