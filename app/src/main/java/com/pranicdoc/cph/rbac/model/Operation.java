package com.pranicdoc.cph.rbac.model;

/**
 * Operation — the fixed verb catalog for RBAC grants. Static Java enum, not
 * a DB-seeded table: unlike the resource/verb/allow-list design V30
 * originally shipped (superseded), the valid (resource, verb) combos are
 * enforced in Java by {@link Resource#supports(Operation)} /
 * {@link Permission}'s constructor, not by a database FK.
 */
public enum Operation {
    READ,
    CREATE,
    UPDATE,
    DELETE,
    EXPORT,
    SHARE,
    WRITE,
    ADMIN,
    IMPORT,
    // Temporarily edit shared (system) rows after confirming the password; master data only.
    ELEVATE
}
