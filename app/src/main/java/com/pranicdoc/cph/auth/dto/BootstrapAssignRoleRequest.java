package com.pranicdoc.cph.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload for the deploy-time role-seeding path (ASSIGN-05):
 * {@code POST /api/v1/bootstrap/assign-role}. It names the target account by
 * its email address rather than by its internal identifier because the caller
 * of this path is a human operator running a one-off seeding step against a
 * freshly cut-over environment — that operator knows the address, not the row
 * key. The role is still named by identifier, resolved against the role
 * catalogue by the assignment service.
 * <p>
 * There is deliberately no account-identifier field: the target is always
 * resolved from the email, so an identifier here would be either redundant or
 * a way to make the email decorative.
 */
public record BootstrapAssignRoleRequest(
        @NotBlank
        @Email
        String email,
        @NotNull UUID roleId
) {}
