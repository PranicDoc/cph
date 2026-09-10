package com.pranicdoc.cph.rbac.baseline;

import com.pranicdoc.cph.rbac.model.RbacRole;
import com.pranicdoc.cph.rbac.repository.RbacRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reconciles the {@code Practitioner} role's {@code role_permission} rows to
 * exactly {@link BaselineRole#GRANTS} — creating the role first if it is
 * absent (normally {@link BaselineRoleSeeder} already made it at startup).
 *
 * <p>Split out of {@link BaselineRoleBackfillService} so this part runs in its
 * own transaction: the service's healer-assignment loop must NOT share a
 * transaction with anything that could mark it rollback-only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BaselineRoleGrantReconciler {

    /** One (resource, verb) grant, both as enum names — the DB's storage form. */
    record GrantKey(String resource, String operation) {}

    /** Outcome of {@link #reconcile(boolean)}. {@code roleId} is null only on a dry run with the role still absent. */
    record ReconcileResult(java.util.UUID roleId, boolean roleCreated, int added, int reactivated, int removed) {}

    private final RbacRoleRepository rbacRoleRepository;
    private final BaselineRoleGrantStore grantStore;

    @Transactional
    public ReconcileResult reconcile(boolean dryRun) {
        Optional<RbacRole> found = rbacRoleRepository.findByName(BaselineRole.NAME);
        boolean roleCreated = found.isEmpty();

        // saveAndFlush: the native insertGrant() calls below carry an FK to
        // rbac_role(id), so the row must be in the DB before they run.
        RbacRole role = found.orElseGet(() -> dryRun ? null : rbacRoleRepository.saveAndFlush(RbacRole.builder()
                .name(BaselineRole.NAME)
                .description(BaselineRole.DESCRIPTION)
                .build()));

        Set<GrantKey> desired = new LinkedHashSet<>();
        BaselineRole.GRANTS.forEach((resource, ops) ->
                ops.forEach(op -> desired.add(new GrantKey(resource.name(), op.name()))));

        if (role == null) {
            // dry run, role not yet created — every grant would be an insert.
            log.info("[dry-run] baseline role '{}' absent: would create it with {} grant(s)",
                    BaselineRole.NAME, desired.size());
            return new ReconcileResult(null, true, desired.size(), 0, 0);
        }

        Map<GrantKey, Boolean> existing = new LinkedHashMap<>();
        for (Object[] row : grantStore.findAllRowsIncludingSoftDeleted(role.getId())) {
            existing.put(new GrantKey((String) row[0], (String) row[1]), (Boolean) row[2]);
        }

        List<GrantKey> toInsert = desired.stream()
                .filter(k -> !existing.containsKey(k))
                .toList();
        List<GrantKey> toReactivate = desired.stream()
                .filter(k -> Boolean.TRUE.equals(existing.get(k)))
                .toList();
        List<GrantKey> toRemove = existing.entrySet().stream()
                .filter(e -> Boolean.FALSE.equals(e.getValue()) && !desired.contains(e.getKey()))
                .map(Map.Entry::getKey)
                .toList();

        log.info("{}baseline role '{}' ({}): +{} new, ^{} re-activated, -{} removed grant(s)",
                dryRun ? "[dry-run] " : "", BaselineRole.NAME, role.getId(),
                toInsert.size(), toReactivate.size(), toRemove.size());

        if (!dryRun) {
            toInsert.forEach(k ->
                    grantStore.insertGrant(role.getId(), k.resource(), k.operation()));
            toReactivate.forEach(k ->
                    grantStore.setGrantDeleted(role.getId(), k.resource(), k.operation(), false));
            toRemove.forEach(k ->
                    grantStore.setGrantDeleted(role.getId(), k.resource(), k.operation(), true));
        }

        return new ReconcileResult(role.getId(), roleCreated,
                toInsert.size(), toReactivate.size(), toRemove.size());
    }
}
