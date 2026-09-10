package com.pranicdoc.cph.rbac.baseline;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * The soft-delete-aware {@code role_permission} operations the baseline-role
 * reconciler needs, kept out of {@code RolePermissionRepository} because the
 * rbac module's Spring Data interfaces are intentionally
 * derived-queries-only (pinned by {@code RbacRepositoryQueryDerivationTest}).
 *
 * <p>Native SQL is unavoidable here: {@code BaseEntity}'s
 * {@code @SQLRestriction("is_deleted = false")} hides soft-deleted rows from
 * every JPA path, yet the {@code (role_id, resource, operation)} unique
 * constraint still counts them — so a re-add of a previously-removed grant
 * has to flip the existing row, not insert a duplicate. Callers run inside
 * the reconciler's transaction.
 */
@Component
public class BaselineRoleGrantStore {

    @PersistenceContext
    private EntityManager em;

    /** Every grant row for a role including soft-deleted ones, as {@code [resource, operation, isDeleted]}. */
    @SuppressWarnings("unchecked")
    public List<Object[]> findAllRowsIncludingSoftDeleted(UUID roleId) {
        return em.createNativeQuery(
                        "SELECT resource, operation, is_deleted FROM role_permission WHERE role_id = :roleId")
                .setParameter("roleId", roleId)
                .getResultList();
    }

    public void insertGrant(UUID roleId, String resource, String operation) {
        em.createNativeQuery("""
                        INSERT INTO role_permission
                            (id, created_at, updated_at, is_deleted, version, role_id, resource, operation)
                        VALUES (gen_random_uuid(), now(), now(), false, 0, :roleId, :resource, :operation)
                        """)
                .setParameter("roleId", roleId)
                .setParameter("resource", resource)
                .setParameter("operation", operation)
                .executeUpdate();
    }

    public void setGrantDeleted(UUID roleId, String resource, String operation, boolean deleted) {
        em.createNativeQuery("""
                        UPDATE role_permission SET is_deleted = :deleted, updated_at = now()
                        WHERE role_id = :roleId AND resource = :resource AND operation = :operation
                        """)
                .setParameter("deleted", deleted)
                .setParameter("roleId", roleId)
                .setParameter("resource", resource)
                .setParameter("operation", operation)
                .executeUpdate();
    }
}
