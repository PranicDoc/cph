package com.pranicdoc.cph.common.audit;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log entity — stores action-level audit trail.
 * Written by AuditAspect, not by Envers (which tracks field-level history).
 * Not tracked by Envers itself — excluded via @Audited(false) override.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@org.hibernate.envers.AuditOverride(forClass = BaseEntity.class, isAudited = false)
public class AuditLog extends BaseEntity {

    @Column(name = "healer_id")
    private UUID healerId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;
}
