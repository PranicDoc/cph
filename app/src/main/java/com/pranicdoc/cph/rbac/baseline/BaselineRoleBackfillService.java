package com.pranicdoc.cph.rbac.baseline;

import com.pranicdoc.cph.rbac.baseline.BaselineRoleGrantReconciler.ReconcileResult;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.service.RoleAssignmentService;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Deploy-time / on-demand backfill for the baseline "Practitioner" role,
 * exposed as {@code POST /api/admin/roles/practitioner/backfill}
 * (see {@link BaselineRoleBackfillController}).
 *
 * <p>Steps, in order:
 * <ol>
 *   <li>{@link BaselineRoleGrantReconciler#reconcile(boolean)} — ensure the
 *       role exists and its grant rows match {@link BaselineRole#GRANTS}.</li>
 *   <li>Assign the role to every active {@code HEALER} that holds no live
 *       role, one {@link RoleAssignmentService#assign} call each so every
 *       assignment gets its own transaction and its own {@code ASSIGN_ROLE}
 *       audit row.</li>
 * </ol>
 *
 * <p>This method is deliberately <b>not</b> {@code @Transactional}: the
 * per-healer {@code assign} calls must not share a transaction that one
 * failure could mark rollback-only. Each collaborator manages its own.
 * Idempotent — safe to re-run any time the matrix changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BaselineRoleBackfillService {

    private final BaselineRoleGrantReconciler grantReconciler;
    private final HealerRepository healerRepository;
    private final RoleAssignmentService roleAssignmentService;

    public BaselineRoleBackfillReport run(boolean dryRun) {
        ReconcileResult grants = grantReconciler.reconcile(dryRun);

        List<UUID> targets = healerRepository.findActiveHealerIdsWithNoLiveRole();
        int assigned = 0;
        int failed = 0;

        if (dryRun) {
            log.info("[dry-run] would assign '{}' to {} healer(s) with no live role",
                    BaselineRole.NAME, targets.size());
        } else if (grants.roleId() != null) {
            for (UUID healerId : targets) {
                try {
                    roleAssignmentService.assign(new RoleAssignmentRequest(healerId, grants.roleId()));
                    assigned++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("backfill: could not assign '{}' to healer {} (continuing): {}",
                            BaselineRole.NAME, healerId, e.toString());
                }
            }
            log.info("backfill: assigned '{}' to {}/{} healer(s) ({} failed)",
                    BaselineRole.NAME, assigned, targets.size(), failed);
        }

        return new BaselineRoleBackfillReport(
                grants.roleId(),
                grants.roleCreated(),
                grants.added(),
                grants.reactivated(),
                grants.removed(),
                targets.size(),
                assigned,
                failed,
                dryRun);
    }
}
