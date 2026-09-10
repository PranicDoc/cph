package com.pranicdoc.cph.rbac.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response contract returned by both assignment mutations. It mirrors the
 * persisted assignment row: its own identifier, the target account, the
 * role, and the moment the authorisation event was recorded.
 * <p>
 * The first component is deliberately named {@code id}. When an audited
 * method has no identifier among its arguments, the audit trail resolves the
 * audited entity reference by reflecting over the returned value: it looks
 * first for an accessor called {@code getId} and then for one called
 * {@code id}. A record component named {@code id} auto-generates the second
 * of those. Renaming this component, or dropping it, would leave the entity
 * reference on every assignment audit row empty.
 */
public record RoleAssignmentResponse(UUID id, UUID principalId, UUID roleId, Instant assignedAt) {}
