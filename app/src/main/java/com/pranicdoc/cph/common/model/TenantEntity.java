package com.pranicdoc.cph.common.model;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-scoped entity. Adds clinicId (NOT NULL) + Hibernate @Filter for row-level isolation.
 * All domain entities that belong to a clinic extend this.
 */
@Getter
@Setter
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "clinicId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "clinic_id = :clinicId")
public abstract class TenantEntity extends BaseEntity {

    @Column(name = "clinic_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID clinicId;

    protected static void populateIds(TenantEntity tenant) {
        UUID clinicId = TenantContext.get();
        if (clinicId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        Instant now = Instant.now();
        tenant.setClinicId(clinicId);
        tenant.setId(com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch());
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
    }
}
