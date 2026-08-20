package com.pranicdoc.cph.rbac.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Update-role request contract -- a full replacement of name, description,
 * and grants (identical component list and annotations to
 * {@link RoleCreateRequest}, including {@code @NotEmpty} on {@code grants}
 * per PD-36-02: a role cannot be edited down to zero grants either).
 * <p>
 * AUDIT-01 obligation: {@code RbacRoleService.update} must take this DTO as
 * its FIRST parameter and the role id SECOND (e.g.
 * {@code update(RoleUpdateRequest request, UUID id)}), because
 * {@code AuditAspect} serializes only {@code args[0]} into the audit
 * payload -- putting the id first (matching {@code ConsentTypeService.rename}'s
 * {@code (id, request)} order) would audit only a bare UUID and lose the
 * actual change content (36-RESEARCH.md Pattern 4).
 */
public record RoleUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotEmpty List<@Valid ResourceGrantDto> grants
) {}
