package com.pranicdoc.cph.common.exception;

/**
 * RBAC-05 — thrown when a role or assignment mutation would strip the last remaining
 * ability to manage roles (i.e. would drive the count of user_role-held
 * (role, assigned-user) pairs granting ROLES:UPDATE to zero).
 */
public class SelfLockoutException extends RuntimeException {
    public SelfLockoutException() {
        super("This change would leave nobody able to manage roles. At least one user must "
                + "retain a role granting the ability to edit roles.");
    }
}
