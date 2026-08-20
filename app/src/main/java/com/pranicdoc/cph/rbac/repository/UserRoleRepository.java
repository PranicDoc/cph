package com.pranicdoc.cph.rbac.repository;

import com.pranicdoc.cph.rbac.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    /** The entry point of Phase 36's effective-permission resolution — always called with the literal "HEALER" today (D-08). */
    List<UserRole> findAllByPrincipalTypeAndPrincipalId(String principalType, UUID principalId);

    /** Backs Phase 38's users-assigned tab. */
    List<UserRole> findAllByRoleId(UUID roleId);

    /** Backs Phase 39's delete-role assigned-user warning. */
    long countByRoleId(UUID roleId);
}
