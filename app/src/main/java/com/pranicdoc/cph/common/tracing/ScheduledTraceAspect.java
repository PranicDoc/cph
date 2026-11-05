package com.pranicdoc.cph.common.tracing;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/** A scheduled run has no request behind it, so each run starts its own IDs. */
@Aspect
@Component
public class ScheduledTraceAspect {

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object traceRun(ProceedingJoinPoint joinPoint) throws Throwable {
        String id = RequestContext.newId();
        RequestContext.set(id, id);
        try {
            return joinPoint.proceed();
        } finally {
            RequestContext.clear();
        }
    }
}
