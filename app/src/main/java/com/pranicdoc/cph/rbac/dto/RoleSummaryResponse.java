package com.pranicdoc.cph.rbac.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Role list-row response contract. No validation annotations -- this is a
 * response record, never bound from a request body.
 * <p>
 * {@code resourceCount} is the count of distinct resources with at least
 * one granted operation. {@code userCount} comes from
 * {@code UserRoleRepository.countByRoleId}. {@code updatedAt} is the role
 * row's last-modified timestamp (from {@code BaseEntity}); the Roles list
 * screen renders it as a relative "Updated" column.
 */
public record RoleSummaryResponse(
        UUID id,
        String name,
        String description,
        int resourceCount,
        long userCount,
        Instant updatedAt
) {}
