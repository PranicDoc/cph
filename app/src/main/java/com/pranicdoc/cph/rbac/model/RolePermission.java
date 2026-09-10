package com.pranicdoc.cph.rbac.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * RolePermission — a role's grant of a (resource, verb) pair. {@code resource}
 * and {@code operation} are stored directly as their enum name (no FK to a
 * catalog table): {@link Resource}/{@link Operation} are static Java enums,
 * not DB-seeded tables, and {@link Permission}'s constructor is the allow-list
 * check — callers must construct a valid {@code Permission} before persisting
 * a grant, so an un-allow-listed combo such as {@code APPROVE} on
 * {@code GLOBAL_CONFIG} is rejected in Java, not by a database constraint.
 * This supersedes the original {@code resourcePermissionId} FK design (PD-04).
 *
 * Follows {@code ProtocolColorMapping}'s join-entity shape: plain columns
 * rather than a JPA relationship mapping, with the uniqueness constraint
 * declared at the JPA level in addition to the DDL constraint.
 */
@Entity
@Table(name = "role_permission",
        uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "resource", "operation"}))
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class RolePermission extends BaseEntity {

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource", nullable = false, length = 40)
    private Resource resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 20)
    private Operation operation;
}
