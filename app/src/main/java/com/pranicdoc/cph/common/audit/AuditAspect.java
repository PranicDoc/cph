package com.pranicdoc.cph.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Arrays;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final SecurityContextUtil securityContextUtil;
    private final HttpServletRequest httpServletRequest;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

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

            // entityId: first UUID arg, then return value getId() fallback
            Object[] args = joinPoint.getArgs();
            UUID entityId = Arrays.stream(args)
                    .filter(a -> a instanceof UUID)
                    .map(a -> (UUID) a)
                    .findFirst()
                    .orElseGet(() -> {
                        if (result == null) return null;
                        try {
                            Object id = result.getClass().getMethod("getId").invoke(result);
                            return id instanceof UUID uid ? uid : null;
                        } catch (Exception ignored2) { return null; }
                    });
            auditLog.setEntityId(entityId);

            // payload: serialize all method args as JSON, capped at 4000 chars
            if (args.length > 0) {
                try {
                    String json = objectMapper.writeValueAsString(args);
                    auditLog.setPayload(json.length() > 4000 ? json.substring(0, 4000) + "…" : json);
                } catch (Exception ignored) {}
            }

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
