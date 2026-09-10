package com.pranicdoc.cph.rbac.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the "users assigned to this role" list (ASSIGN-01). No
 * validation annotations — this is a response record, never bound from a
 * request body.
 * <p>
 * {@code name} and {@code email} are copied from the matching {@code Healer}
 * record at read time. Both are nullable: {@code user_role.principal_id}
 * carries no foreign key, so an assignment row can outlive the account it
 * points at. Such an orphaned row still appears here with a null name and
 * email rather than being dropped or raising an error.
 */
public record RoleAssignedUserResponse(UUID userId, String name, String email, Instant assignedAt) {}
