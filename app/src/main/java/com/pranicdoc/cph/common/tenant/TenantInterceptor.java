package com.pranicdoc.cph.common.tenant;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import jakarta.persistence.EntityManager;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * HandlerInterceptor that enables the Hibernate @Filter("tenantFilter")
 * on every authenticated request. This ensures all JPA queries are
 * automatically scoped to the current tenant (clinic).
 */
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private final EntityManager entityManager;
    private final SecurityContextUtil securityContextUtil;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        try {
            UUID clinicId = securityContextUtil.getCurrentClinicId();
            if (clinicId == null) {
                throw new IllegalStateException("Missing clinicId");
            }
            TenantContext.set(clinicId);
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("clinicId", clinicId);
        } catch (IllegalStateException ignored) {
            // No auth context (public endpoints) — skip filter
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
