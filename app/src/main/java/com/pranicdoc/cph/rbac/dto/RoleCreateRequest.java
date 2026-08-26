package com.pranicdoc.cph.rbac.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create-role request contract (ROLE-10, D-02).
 * <p>
 * {@code description} is deliberately OPTIONAL (PD-36-01) -- length-limited
 * only, with no non-blank requirement -- matching {@code rbac_role.description
 * VARCHAR(500)}, which is nullable in V33. {@code grants} must be non-empty
 * (PD-36-02): a role with zero grants is a
 * "declared but toothless" role, exactly the failure class PITFALLS.md
 * warns this milestone exists to prevent. Deleting a role is the supported
 * way to remove all its access, not editing it down to zero grants.
 */
public record RoleCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotEmpty List<@Valid ResourceGrantDto> grants
) {}
