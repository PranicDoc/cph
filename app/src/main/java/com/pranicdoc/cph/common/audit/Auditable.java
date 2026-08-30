package com.pranicdoc.cph.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation for service methods that should be logged to audit_log.
 * Used by AuditAspect (AOP) to intercept and record actions.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * Description of the action being performed, e.g. "CREATE_PATIENT", "UPDATE_CASE".
     */
    String action();

    /**
     * Boolean for deciding should the payload be logged as well, by default it is true
     */
    boolean auditPayload() default true;
}
