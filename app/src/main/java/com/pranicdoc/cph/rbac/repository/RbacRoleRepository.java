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
}
