package com.pranicdoc.cph.rbac.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

/**
 * RbacRole — the Phase 35 (RBAC-01) dynamic role entity. A row here is a
 * custom, admin-created role (e.g. "Billing Ops", "Content Reviewer") that
 * Phase 36's role CRUD service creates, edits and deletes, and that Phase 37
 * assigns to principals via {@link UserRole}.
 *
 * NAMING-COLLISION RESOLUTION — named {@code RbacRole} rather than bare
 * {@code Role} because {@code com.pranicdoc.cph.common.enums.Role} is the
 * unrelated static login-tier enum ({@code HEALER}/{@code SENIOR_HEALER}/
 * {@code RECEPTIONIST}/{@code ADMIN}/{@code SUPADM}). That enum stays
 * untouched through Phase 40 and shrinks to {@code HEALER}/{@code SUPADM}
 * only in Phase 41 (D-01) — it is never migrated into this table.
 * {@code SUPADM} in particular is never represented as a row in
 * {@code rbac_role}: it is an unconditional hardcoded bypass (D-02), not a
 * grantable dynamic role.
 *
 * PD-03 — deliberately no protected-seeded-role flag field (the pattern
 * {@code ConsentType}'s admin-mutation guard column uses). Such a flag is
 * unnecessary here because Phase 35 seeds zero rows into this table; there
 * is no seeded row to protect from deletion.
 */
@Entity
@Table(name = "rbac_role")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class RbacRole extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;
}
