package com.pranicdoc.cph.rbac.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /api/admin/roles/{id}/users} — the single
 * target account to attach to a role (ASSIGN-02).
 * <p>
 * The wire key is {@code userId}, not {@code principalId}. This endpoint is a
 * sub-resource scoped under {@code /api/admin/roles/{id}/users}, where "the
 * user being added to this role" reads more naturally at the URL level than
 * the persistence layer's polymorphic {@code principal} vocabulary
 * (38-RESEARCH Open Question Q2). The role identifier is never carried in
 * this body — it is the {@code {id}} path variable. The controller maps
 * {@code userId} onto {@link RoleAssignmentRequest#principalId()} when it
 * builds the whole {@link RoleAssignmentRequest} record the audited
 * assignment service requires.
 */
public record RoleUserAssignmentBody(@NotNull UUID userId) {}
