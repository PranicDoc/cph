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

    private static final int PAYLOAD_MAX_LENGTH = 4000;
    private static final int MESSAGE_MAX_LENGTH = 2000;

    private final AuditLogRepository auditLogRepository;
    private final SecurityContextUtil securityContextUtil;
    private final HttpServletRequest httpServletRequest;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    /**
     * Records one audit_log row per invocation regardless of outcome. Previously
     * the write only happened after joinPoint.proceed() returned successfully, so
     * any exception out of an audited method (including what surfaces to the
     * caller as a 500) left no audit trail at all — the exception just propagated
     * past the write and was logged, at best, to the application log by
     * GlobalExceptionHandler. Both branches below write a row before returning
     * or rethrowing, so success and failure are equally represented.
     */
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object[] args = joinPoint.getArgs();
        try {
            Object result = joinPoint.proceed();
            recordAuditLog(joinPoint, auditable, args, result, AuditStatus.SUCCESS, null);
            return result;
        } catch (Throwable ex) {
            recordAuditLog(joinPoint, auditable, args, null, AuditStatus.FAILURE, ex);
            throw ex;
        }
    }

    private void recordAuditLog(ProceedingJoinPoint joinPoint, Auditable auditable, Object[] args,
                                 Object result, AuditStatus status, Throwable failure) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setAction(auditable.action());
            auditLog.setPerformedAt(Instant.now());
            auditLog.setIpAddress(httpServletRequest.getRemoteAddr());
            auditLog.setEntityType(joinPoint.getTarget().getClass().getSimpleName());
            auditLog.setStatus(status);
            auditLog.setMessage(resolveMessage(status, failure));

            try {
                auditLog.setHealerId(securityContextUtil.getCurrentHealerId());
            } catch (IllegalStateException ignored) {}

            // entityId: first UUID arg, then return value getId()/id() fallback.
            // Every DTO in the consent module (ConsentResponse, PolicyVersionResponse)
            // is a Java record, whose component accessor is id() rather than getId() —
            // AUDIT-03 depends on AuditLog.entityId pointing at the saved policy_version
            // row so the audit trail can be joined back to the version number and
            // effective date (D-05). Both reflective lookups stay inside this
            // swallow-and-return-null guard so audit reflection can never fail an
            // audited method, and the instanceof UUID narrowing still guards against a
            // non-UUID id accessor. On failure `result` is null, so this always falls
            // back to null rather than throwing.
            UUID entityId = Arrays.stream(args)
                    .filter(a -> a instanceof UUID)
                    .map(a -> (UUID) a)
                    .findFirst()
                    .orElseGet(() -> resolveEntityIdFromResult(result));
            auditLog.setEntityId(entityId);

            // payload: serialize all method args as JSON, capped at 4000 chars.
            // Captured on both outcomes so a failed call's incoming request is
            // still visible in the trail, not just successful ones.
            if (args.length > 0 && !"LOGIN".equalsIgnoreCase(auditable.action())) {
                try {
                    String json = objectMapper.writeValueAsString(args[0]);
                    auditLog.setPayload(truncate(json, PAYLOAD_MAX_LENGTH));
                } catch (Exception ignored) {}
            }

            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.execute(txStatus -> {
                auditLogRepository.save(auditLog);
                return null;
            });
        } catch (Exception ex) {
            log.error("Failed to write audit log for action: {}", auditable.action(), ex);
        }
    }

    private static String resolveMessage(AuditStatus status, Throwable failure) {
        if (status == AuditStatus.SUCCESS) {
            return "Success";
        }
        String detail = failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
        return truncate(detail, MESSAGE_MAX_LENGTH);
    }

    private static UUID resolveEntityIdFromResult(Object result) {
        if (result == null) return null;
        try {
            Object id = result.getClass().getMethod("getId").invoke(result);
            return id instanceof UUID uid ? uid : null;
        } catch (NoSuchMethodException noGetId) {
            try {
                Object id = result.getClass().getMethod("id").invoke(result);
                return id instanceof UUID uid ? uid : null;
            } catch (Exception ignored2) { return null; }
        } catch (Exception ignored2) { return null; }
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
