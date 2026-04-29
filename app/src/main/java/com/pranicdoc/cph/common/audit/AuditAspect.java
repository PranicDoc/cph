package com.pranicdoc.cph.common.audit;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * AOP aspect that intercepts methods annotated with @Auditable
 * and writes an entry to the audit_log table.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final SecurityContextUtil securityContextUtil;
    private final HttpServletRequest httpServletRequest;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        // Execute the original method
        Object result = joinPoint.proceed();

        // Build audit log entry
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setAction(auditable.action());
            auditLog.setPerformedAt(Instant.now());
            auditLog.setIpAddress(httpServletRequest.getRemoteAddr());

            try {
                auditLog.setHealerId(securityContextUtil.getCurrentHealerId());
                auditLog.setClinicId(securityContextUtil.getCurrentClinicId());
            } catch (IllegalStateException ignored) {
                // No auth context (e.g., system operations)
            }

            // Capture entity type from the method's declaring class
            auditLog.setEntityType(joinPoint.getTarget().getClass().getSimpleName());

            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            // Audit failure should not break the main flow
            log.error("Failed to write audit log for action: {}", auditable.action(), ex);
        }

        return result;
    }
}
