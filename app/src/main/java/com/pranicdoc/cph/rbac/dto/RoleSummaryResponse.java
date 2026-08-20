package com.pranicdoc.cph.rbac.dto;

import java.util.UUID;

/**
 * Role list-row response contract. No validation annotations -- this is a
 * response record, never bound from a request body.
 * <p>
 * {@code resourceCount} is the count of distinct resources with at least
 * one granted operation. {@code userCount} comes from
 * {@code UserRoleRepository.countByRoleId} and is always 0 until Phase 37
 * ships role-to-user assignment.
 */
public record RoleSummaryResponse(
        UUID id,
        String name,
        String description,
        int resourceCount,
        long userCount
) {}
