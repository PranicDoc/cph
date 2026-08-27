package com.pranicdoc.cph.rbac.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request contract for attaching or detaching one role to/from one principal.
 * It carries both identifiers the mutation concerns: the target account and
 * the role.
 * <p>
 * This record must be handed to the assignment service's methods whole, as
 * the single method argument, and must never be broken apart into two loose
 * identifier parameters. The audit trail serialises only the first argument
 * of an audited method into its stored payload; passing the account
 * identifier and the role identifier as two separate parameters would leave
 * every assignment audit entry showing nothing but a bare account
 * identifier, with no record of which role was granted or removed. Keeping
 * both identifiers inside one payload object is what makes the audit record
 * of a privilege change complete.
 */
public record RoleAssignmentRequest(
        @NotNull UUID principalId,
        @NotNull UUID roleId
) {}
