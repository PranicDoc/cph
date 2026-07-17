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
 * automatically scoped to the current tenant (healer).
 *
 * NOTE (26-03): PATIENT_CASE/SELF_REG sessions have no real healerId yet
 * (D-26-04, resolved in 26-11) — those requests fall into the catch below
 * and run with the filter skipped, a known deliberate transient state
 * covered by JwtClaimShapeTest's @Disabled patient-token test.
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
            UUID healerId = securityContextUtil.getCurrentHealerId();
            if (healerId == null) {
                throw new IllegalStateException("Missing healerId");
            }
            TenantContext.set(healerId);
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("healerId", healerId);
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
