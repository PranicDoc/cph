package com.pranicdoc.cph.auth.model;

import com.pranicdoc.cph.common.enums.HealingLevel;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

/**
 * Healer entity — a practitioner belonging to a clinic.
 * Tenant-scoped (clinicId from TenantEntity).
 */
@Entity
@Table(name = "healer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class Healer extends TenantEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "healing_level", length = 20)
    private HealingLevel healingLevel;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
