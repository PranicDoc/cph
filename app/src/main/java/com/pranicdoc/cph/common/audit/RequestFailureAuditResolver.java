package com.pranicdoc.cph.common.audit;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

import java.time.Instant;

/**
 * Writes one FAILURE audit_log row for every exception that escapes a controller
 * handler, so the trail shows which operation was attempted, by whom and when —
 * including failures AuditAspect can never see because they happen before the
 * @Auditable service method is entered (request-body binding, @Valid,
 * @PreAuthorize denials, controller-level exceptions).
 *
 * Runs first (HIGHEST_PRECEDENCE) but always returns null, so the exception is
 * still resolved by GlobalExceptionHandler exactly as before; this class only
 * observes. It also never throws — a failed audit write must not change the
 * response the caller gets.
 *
 * Exceptions thrown inside an @Auditable method already have a row from
 * AuditAspect, which flags the request with {@link AuditAspect#AUDITED_ATTRIBUTE};
 * those are skipped here so one failure yields one row. Requests that matched no
 * handler at all (404 probes) are skipped too — there is no operation to record.
 * Failures in servlet filters run before MVC dispatch and are out of reach.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestFailureAuditResolver implements HandlerExceptionResolver, Ordered {

    private static final int ACTION_MAX_LENGTH = 255;
    private static final int MESSAGE_MAX_LENGTH = 10_000;

    private final AuditLogWriter auditLogWriter;
    private final SecurityContextUtil securityContextUtil;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                         Object handler, Exception ex) {
        if (handler instanceof HandlerMethod handlerMethod
                && request.getAttribute(AuditAspect.AUDITED_ATTRIBUTE) == null) {
            recordFailure(request, handlerMethod, ex);
        }
        return null;
    }

    private void recordFailure(HttpServletRequest request, HandlerMethod handlerMethod, Exception ex) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setAction(truncate(operation(request), ACTION_MAX_LENGTH));
            auditLog.setEntityType(handlerMethod.getBeanType().getSimpleName());
            auditLog.setStatus(AuditStatus.FAILURE);
            auditLog.setPerformedAt(Instant.now());
            auditLog.setIpAddress(request.getRemoteAddr());
            auditLog.setMessage(truncate(
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(), MESSAGE_MAX_LENGTH));

            try {
                auditLog.setHealerId(securityContextUtil.getCurrentHealerIdOrNull());
            } catch (RuntimeException ignored) {}

            auditLogWriter.enqueue(auditLog);
        } catch (Exception auditFailure) {
            log.error("Failed to write request-failure audit log for {} {}",
                    request.getMethod(), request.getRequestURI(), auditFailure);
        }
    }

    /** "POST /api/patients/register" — the route template, so ids don't fragment the trail. */
    private static String operation(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return request.getMethod() + " " + (pattern != null ? pattern : request.getRequestURI());
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
