package com.pranicdoc.cph.rbac.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Role detail response contract. No validation annotations -- this is a
 * response record, never bound from a request body.
 * <p>
 * The {@code id()} accessor is the record-style accessor
 * {@code AuditAspect}'s entityId fallback already handles, so
 * {@code RbacRoleService.create} gets a correct {@code entityId} even
 * though no UUID argument is present in its own method signature.
 */
public record RoleResponse(
        UUID id,
        String name,
        String description,
        List<ResourceGrantDto> grants,
        Instant createdAt,
        Instant updatedAt
) {}
