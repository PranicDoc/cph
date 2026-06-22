package com.pranicdoc.cph.common.audit;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final SecurityContextUtil securityContextUtil;
    private final HttpServletRequest httpServletRequest;
    private final PlatformTransactionManager transactionManager;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setAction(auditable.action());
            auditLog.setPerformedAt(Instant.now());
            auditLog.setIpAddress(httpServletRequest.getRemoteAddr());
            auditLog.setEntityType(joinPoint.getTarget().getClass().getSimpleName());

            try {
                auditLog.setHealerId(securityContextUtil.getCurrentHealerId());
                auditLog.setClinicId(securityContextUtil.getCurrentClinicId());
            } catch (IllegalStateException ignored) {}

            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.execute(status -> {
                auditLogRepository.save(auditLog);
                return null;
            });
        } catch (Exception ex) {
            log.error("Failed to write audit log for action: {}", auditable.action(), ex);
        }

        return result;
    }
}
