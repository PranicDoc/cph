package com.pranicdoc.cph.rbac.baseline;

import java.util.UUID;

/**
 * Result of {@code POST /api/admin/roles/practitioner/backfill}.
 *
 * @param roleId              the Practitioner role id (null only on a dry run where the role was still absent)
 * @param roleCreated         true if this call created the role row
 * @param grantsAdded         grant rows inserted to match {@link BaselineRole#GRANTS}
 * @param grantsReactivated   previously soft-deleted grant rows flipped back live
 * @param grantsRemoved       live grant rows soft-deleted because they are no longer in the matrix
 * @param healersNeedingRole  active HEALER accounts found holding no live role
 * @param healersAssigned     of those, how many were assigned the role this call (0 on a dry run)
 * @param healersFailed       of those, how many the assignment call raised on (logged, not fatal)
 * @param dryRun              echo of the request flag — when true nothing above was written
 */
public record BaselineRoleBackfillReport(
        UUID roleId,
        boolean roleCreated,
        int grantsAdded,
        int grantsReactivated,
        int grantsRemoved,
        int healersNeedingRole,
        int healersAssigned,
        int healersFailed,
        boolean dryRun) {}
