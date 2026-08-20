package com.pranicdoc.cph.rbac;

import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Permission;
import com.pranicdoc.cph.rbac.model.Resource;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression gate over the static {@link Resource}/{@link Operation} catalog
 * that replaced V30's original DB-seeded resource/permission/resource_permission
 * tables (see {@code V30RbacSchemaMigrationTest}'s class comment). This class
 * plays the same role the removed {@code seedsExactlyEightResources}/
 * {@code seedsAllSevenCanonicalVerbs}/{@code resourcePermissionAllowListMatchesD16}
 * DB-seed tests used to: pin the catalog shape so a change is a deliberate,
 * reviewed edit here, not an accidental enum-constant typo.
 */
class RbacResourceOperationCatalogTest {

    @Test
    void declaresExactlyFifteenResources() {
        assertEquals(15, Resource.values().length, "Resource catalog size drifted — update this test deliberately if intended");
    }

    @Test
    void declaresExactlyEightOperations() {
        assertEquals(8, Operation.values().length, "Operation catalog size drifted — update this test deliberately if intended");
    }

    @Test
    void rolesResourceExistsForRoleCrud() {
        assertEquals(Set.of(Operation.READ, Operation.CREATE, Operation.UPDATE, Operation.DELETE),
                Resource.ROLES.getSupportedOperations());
    }

    @Test
    void accountsResourceIsDistinctFromCreditAccounts() {
        assertEquals(Set.of(Operation.READ, Operation.UPDATE, Operation.DELETE),
                Resource.ACCOUNTS.getSupportedOperations());
        assertEquals(Set.of(Operation.READ), Resource.CREDIT_ACCOUNTS.getSupportedOperations());
    }

    @Test
    void legalDocumentsSupportsAdminVerbForSensitiveToggle() {
        assertTrue(Resource.LEGAL_DOCUMENTS.supports(Operation.ADMIN));
    }

    @Test
    void creditAccountTransactionsSupportsWriteVerb() {
        assertTrue(Resource.CREDIT_ACCOUNT_TRANSACTIONS.supports(Operation.WRITE));
    }

    @Test
    void permissionConstructorRejectsUnsupportedCombo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Permission(Resource.GLOBAL_CONFIG, Operation.DELETE),
                "GLOBAL_CONFIG does not support DELETE — Permission's constructor is the allow-list check");
    }

    @Test
    void permissionConstructorAcceptsSupportedCombo() {
        Permission permission = new Permission(Resource.ROLES, Operation.READ);
        assertEquals(Resource.ROLES, permission.resource());
        assertEquals(Operation.READ, permission.operation());
    }
}
