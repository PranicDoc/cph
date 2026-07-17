package com.pranicdoc.cph.common.tenant;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import jakarta.persistence.PrePersist;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.pranicdoc.cph.common.model.TenantEntity;

/**
 * JPA entity listener that automatically sets healerId on tenant-scoped entities
 * before they are persisted. Reads healerId from the SecurityContext (set by JWT filter).
 */
@Component
@RequiredArgsConstructor
public class TenantEntityListener {

    private final SecurityContextUtil securityContextUtil;

    @PrePersist
    public void setHealerId(Object entity) {
        if (entity instanceof TenantEntity tenantEntity) {
            if (tenantEntity.getHealerId() == null) {
                tenantEntity.setHealerId(securityContextUtil.getCurrentHealerId());
            }
        }
    }
}
