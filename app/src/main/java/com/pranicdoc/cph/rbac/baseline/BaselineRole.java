package com.pranicdoc.cph.rbac.baseline;

import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;

import java.util.Map;
import java.util.Set;

/**
 * The default working role every healer gets on onboarding so the Phase-42
 * capability gates don't lock a freshly-created account out of the core app.
 *
 * <p>Single source of truth for the grant matrix. Read by
 * {@link BaselineRoleSeeder} (startup seed, all profiles) and
 * {@link BaselineRoleGrantReconciler} (the {@code POST /api/admin/roles/
 * practitioner/backfill} endpoint), so a matrix change here reaches both the
 * in-app seed and the deploy-time backfill.
 *
 * <p>Grants cover the healer-facing surface only (patient / case / session /
 * pdf / scan / session-protocol / feedback / credit) plus master data — no
 * admin resources (ROLES, GLOBAL_CONFIG, LEGAL_DOCUMENTS, ACCOUNTS,
 * HEALER_REG, PACKAGES). It is a plain role: not protected by an
 * {@code is_system} flag (deferred), so an operator can still edit or delete
 * it via the Roles UI.
 *
 * <p><b>MASTER_DATA_PROTOCOLS is CREATE/UPDATE/DELETE, not read-only</b> —
 * this is what lets a healer add/edit/remove their own private chakra/color
 * associations on a protocol (see {@code MasterDataController}'s
 * {@code /protocols/{id}/chakra-colors} endpoints). Every mutating handler
 * behind this grant is ownership-checked in {@code MasterDataService}
 * (system rows, healerId null, stay SUPADM-only via the non-grantable
 * MASTER_DATA_PROTOCOLS:ADMIN override — see PD-40-04), so this grant cannot
 * let a healer touch system data or another healer's rows. It does also
 * unlock {@code POST /protocols/import} (bulk xlsx) and the
 * {@code /protocol-mappings} CRUD for every healer, since both share this
 * same resource — accepted, not an oversight.
 */
public final class BaselineRole {

    private BaselineRole() {}

    public static final String NAME = "Practitioner";

    public static final String DESCRIPTION =
            "Default working role assigned to every healer on onboarding. "
            + "Covers day-to-day patient, case, session, PDF, scan, protocol, "
            + "feedback and credit operations plus read-only reference data.";

    /** Resource -&gt; granted verbs. Every pair must satisfy {@code Resource.supports(Operation)}. */
    public static final Map<Resource, Set<Operation>> GRANTS = Map.ofEntries(
            Map.entry(Resource.PATIENT, Set.of(Operation.CREATE, Operation.READ, Operation.UPDATE)),
            Map.entry(Resource.CASE, Set.of(Operation.CREATE, Operation.READ, Operation.UPDATE)),
            Map.entry(Resource.SESSION, Set.of(Operation.CREATE, Operation.READ, Operation.UPDATE)),
            Map.entry(Resource.PDF, Set.of(Operation.CREATE, Operation.READ, Operation.EXPORT)),
            Map.entry(Resource.SCAN, Set.of(Operation.CREATE)),
            Map.entry(Resource.SESSION_PROTOCOL, Set.of(Operation.CREATE)),
            Map.entry(Resource.FEEDBACK, Set.of(Operation.CREATE)),
            Map.entry(Resource.CREDIT_ACCOUNTS, Set.of(Operation.READ, Operation.CREATE)),
            Map.entry(Resource.CREDIT_ACCOUNT_TRANSACTIONS, Set.of(Operation.READ, Operation.WRITE)),
            // Read-only reference data a healer needs to record scans / protocols.
            Map.entry(Resource.MASTER_DATA_CHAKRAS, Set.of(Operation.READ)),
            Map.entry(Resource.MASTER_DATA_COLORS, Set.of(Operation.READ)),
            // CREATE/UPDATE/DELETE (not read-only) so a healer can self-serve their own
            // protocol-chakra-color additions — see the class doc above.
            Map.entry(Resource.MASTER_DATA_PROTOCOLS,
                    Set.of(Operation.READ, Operation.CREATE, Operation.UPDATE, Operation.DELETE)));
}
