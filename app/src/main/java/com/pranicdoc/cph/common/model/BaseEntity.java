package com.pranicdoc.cph.common.model;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.UUID;

/**
 * Base entity with UUIDv7 PK, audit timestamps, soft-delete.
 * All domain entities extend this (directly or via TenantEntity).
 */
@Getter
@Setter
@MappedSuperclass
@Audited
@SQLRestriction("is_deleted = false")
public abstract class BaseEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Version
    private long version;

    /**
     * Generate UUIDv7 and set audit timestamps on first persist.
     */
    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch(); // UUIDv7
        }
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Update timestamp on every update.
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Soft delete — marks entity as deleted without physical removal.
     */
    public void softDelete() {
        this.isDeleted = true;
    }
}
