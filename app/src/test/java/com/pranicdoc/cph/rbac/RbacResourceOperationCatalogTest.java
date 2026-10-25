package com.pranicdoc.cph.rbac;

import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Permission;
import com.pranicdoc.cph.rbac.model.Resource;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void declaresExactlyEighteenResources() {
        assertEquals(18, Resource.values().length, "Resource catalog size drifted — update this test deliberately if intended");
    }

    @Test
    void declaresExactlyTenOperations() {
        assertEquals(10, Operation.values().length, "Operation catalog size drifted — update this test deliberately if intended");
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
        assertEquals(Set.of(Operation.READ, Operation.CREATE, Operation.ADMIN),
                Resource.CREDIT_ACCOUNTS.getSupportedOperations(),
                "CREDIT_ACCOUNTS gained CREATE per D-04 — CreditProxyController.createAccount "
                        + "(POST /api/credits/account) has no other fitting verb — and ADMIN for the "
                        + "cross-healer admin Accounts screen");
    }

    @Test
    void scanResourceSupportsOnlyCreate() {
        assertEquals(Set.of(Operation.CREATE), Resource.SCAN.getSupportedOperations(),
                "SCAN is enforced solely by ScanController.saveScan "
                        + "(POST /api/v1/sessions/{sessionId}/scans); a later 'for completeness' "
                        + "UPDATE/DELETE with no enforcing endpoint must fail this build (D-01/D-02)");
    }

    @Test
    void sessionProtocolResourceSupportsOnlyCreate() {
        assertEquals(Set.of(Operation.CREATE), Resource.SESSION_PROTOCOL.getSupportedOperations(),
                "SESSION_PROTOCOL is enforced solely by ProtocolController.saveProtocol "
                        + "(POST /api/v1/sessions/{sessionId}/protocol); an added verb with no "
                        + "enforcing endpoint must fail this build (D-01/D-02)");
    }

    @Test
    void feedbackResourceSupportsOnlyCreate() {
        assertEquals(Set.of(Operation.CREATE), Resource.FEEDBACK.getSupportedOperations(),
                "FEEDBACK is enforced solely by CaseController.resendFeedback; D-19 dropped READ "
                        + "because the only candidate read endpoint, CaseController.getSymptomsForFeedback, "
                        + "is reachable only via a PATIENT-role token where @rbacAuthz returns false for "
                        + "every real caller — a FEEDBACK:READ gate would 403 the patient feedback form");
    }

    @Test
    void sessionProtocolIsDistinctFromMasterDataProtocols() {
        assertNotEquals(Resource.SESSION_PROTOCOL, Resource.MASTER_DATA_PROTOCOLS,
                "SC2 / D-06: the healer per-session protocol resource must be a distinct enum "
                        + "constant from the admin master-data protocol catalog");
        assertEquals(Set.of(Operation.CREATE), Resource.SESSION_PROTOCOL.getSupportedOperations());
        assertEquals(Set.of(Operation.READ, Operation.CREATE, Operation.UPDATE, Operation.DELETE, Operation.IMPORT, Operation.ELEVATE),
                Resource.MASTER_DATA_PROTOCOLS.getSupportedOperations());
        assertNotEquals(Resource.SESSION_PROTOCOL.getSupportedOperations(),
                Resource.MASTER_DATA_PROTOCOLS.getSupportedOperations(),
                "a healer-session protocol grant can never be satisfied by an admin master-data grant");
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
