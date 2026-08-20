package com.pranicdoc.cph.rbac.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.UUID;

/**
 * UserRole — the principal-to-role assignment record (Phase 35, RBAC-01).
 * This table is written for the first time by Phase 37's assignment
 * service and Phase 40's ADMIN-tier backfill; V30 seeds zero rows into it.
 *
 * NAMING-COLLISION RESOLUTION — this entity is {@code UserRole} on table
 * {@code user_role}, and has zero relationship to
 * {@code common.enums.Role}, the static login-tier enum (STACK.md's
 * collision warning). Same class of naming-disambiguation issue
 * {@code Consent.java} documents for its own {@code policyVersion} field
 * versus the inherited {@code version} column.
 *
 * {@code principalType}/{@code principalId} is deliberately polymorphic and
 * FK-free (D-08) — always the literal {@code "HEALER"} today, extensible to
 * other principal types later without schema rework. Existence of the
 * principal is validated at the service layer in Phase 37, not by the
 * database — the same trade-off {@code Consent.subjectId} already accepted.
 *
 * {@code assignedAt}/{@code assignedBy} exist as explicit domain columns
 * distinct from the inherited {@code createdAt}/{@code createdBy}: they
 * record the authorization event, which may diverge from row creation,
 * exactly as {@code Consent.acceptedBy} diverges from
 * {@code Consent.createdBy}. This entity carries no grouping dimension of
 * any kind — it records only "this principal holds this role," never
 * "this role applies to this group of healers" (see PITFALLS.md Pitfall 8;
 * no multi-user grouping construct is reintroduced here).
 */
@Entity
@Table(name = "user_role",
        uniqueConstraints = @UniqueConstraint(columnNames = {"principal_type", "principal_id", "role_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class UserRole extends BaseEntity {

    /** Always the literal "HEALER" today (D-08); extensible without schema rework. */
    @Column(name = "principal_type", nullable = false, length = 30)
    private String principalType;

    /** In practice, healer.id — deliberately not FK-bound, mirroring consent.subject_id. */
    @Column(name = "principal_id", nullable = false)
    private UUID principalId;

    /** Backed by a real FK at the DDL level, to rbac_role(id). */
    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by")
    private UUID assignedBy;
}
