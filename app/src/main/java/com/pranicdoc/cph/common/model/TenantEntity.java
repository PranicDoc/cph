package com.pranicdoc.cph.common.model;

import com.pranicdoc.cph.common.tenant.TenantContext;
import com.pranicdoc.cph.common.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-scoped entity. Adds healerId (NOT NULL) + Hibernate @Filter for row-level isolation.
 * All domain entities that belong to a healer extend this.
 */
@Getter
@Setter
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "healerId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "healer_id = :healerId")
@EntityListeners(TenantEntityListener.class)
public abstract class TenantEntity extends BaseEntity {

    @Column(name = "healer_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID healerId;

    protected static void populateIds(TenantEntity tenant) {
        UUID healerId = TenantContext.get();
        if (healerId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        Instant now = Instant.now();
        tenant.setHealerId(healerId);
        tenant.setId(com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch());
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
    }

    // BRIDGE (removed in 26-09): pre-cutover callers read the tenant via getClinicId
    @Deprecated
    public UUID getClinicId() {
        return getHealerId();
    }

    // BRIDGE (removed in 26-09): pre-cutover callers write the tenant via setClinicId
    @Deprecated
    public void setClinicId(UUID id) {
        setHealerId(id);
    }
}
