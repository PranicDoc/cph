package com.pranicdoc.cph.rbac.repository;

import com.pranicdoc.cph.rbac.model.RbacRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RbacRoleRepository extends JpaRepository<RbacRole, UUID> {

    /** Phase 40's backfill uses this to resolve the seeded legacy-administrator role by name. */
    Optional<RbacRole> findByName(String name);

    /** The guard Phase 36 Success Criterion 1 (duplicate role name rejected with a clear validation error) calls before insert. */
    boolean existsByName(String name);

    /**
     * ROLE-10's case-insensitive pre-check (D-04): does a role with this
     * name already exist, ignoring case? {@code RbacRoleService.create}
     * calls this before insert for the fast, friendly-error path; the
     * database-level {@code uq_rbac_role_name_ci} functional unique index
     * (V34) is the race-safe backstop underneath it. Because {@code
     * BaseEntity} carries {@code @SQLRestriction("is_deleted = false")},
     * this method does not see soft-deleted roles -- a documented,
     * accepted consequence of PD-36-03 (a re-created name that collides
     * with a soft-deleted row's name surfaces as a 409 from the V34 index
     * instead of this method's friendly 400).
     */
    boolean existsByNameIgnoreCase(String name);
}
