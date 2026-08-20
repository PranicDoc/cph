package com.pranicdoc.cph.rbac.model;

import java.util.Set;

/**
 * Resource — the fixed resource catalog for RBAC grants. Static Java enum,
 * not a DB-seeded table (superseding V30's original {@code resource} table +
 * {@code resource_permission} allow-list design). {@link #supports(Operation)}
 * is the allow-list check now: {@link Permission}'s constructor calls it at
 * write time, so an invalid (resource, verb) combo is rejected in Java
 * before it ever reaches {@code role_permission} — there is no DB CHECK
 * constraint backing this.
 */
public enum Resource {

    PATIENT(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE,
            Operation.EXPORT
    ),

    SESSION(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE
    ),

    CASE(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE,
            Operation.EXPORT,
            Operation.SHARE
    ),

    PDF(
            Operation.READ,
            Operation.CREATE,
            Operation.DELETE,
            Operation.EXPORT,
            Operation.SHARE
    ),

    CREDIT_ACCOUNTS(
            Operation.READ
    ),

    CREDIT_ACCOUNT_TRANSACTIONS(
            Operation.READ,
            Operation.WRITE
    ),

    GLOBAL_CONFIG(
            Operation.READ,
            Operation.UPDATE
    ),

    PACKAGES(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE
    ),

    MASTER_DATA_COLORS(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE
    ),

    MASTER_DATA_PROTOCOLS(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE
    ),

    MASTER_DATA_CHAKRAS(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE
    ),

    HEALER_REG(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE
    ),

    LEGAL_DOCUMENTS(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE,
            Operation.ADMIN
    ),

    ROLES(
            Operation.READ,
            Operation.CREATE,
            Operation.UPDATE,
            Operation.DELETE
    ),

    /** Healer account administration (list/activate/deactivate/delete) — distinct from CREDIT_ACCOUNTS' billing reads. */
    ACCOUNTS(
            Operation.READ,
            Operation.UPDATE,
            Operation.DELETE
    );

    private final Set<Operation> supportedOperations;

    Resource(Operation... supportedOperations) {
        this.supportedOperations = Set.of(supportedOperations);
    }

    public Set<Operation> getSupportedOperations() {
        return supportedOperations;
    }

    public boolean supports(Operation operation) {
        return supportedOperations.contains(operation);
    }
}
