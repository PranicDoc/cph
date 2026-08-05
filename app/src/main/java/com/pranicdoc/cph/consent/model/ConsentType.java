package com.pranicdoc.cph.consent.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

/**
 * ConsentType — the admin-manageable consent-form registry (Phase 31.1,
 * CONSENT-09/CONSENT-10/CONSENT-11). Replaces the former hardcoded
 * ConsentType enum (Phase 30); {@code ConsentService} iterates this table
 * instead of a compile-time constant list, so a new admin-created type is
 * picked up with no code change. {@code key} is the same denormalized,
 * non-FK string join target used by {@code Consent.consentType} and
 * {@code PolicyVersion.consentType} — existence/active-state is enforced in
 * the application layer (see {@code ConsentService.publish()}), not the DB.
 */
@Entity
@Table(name = "consent_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ConsentType extends BaseEntity {

    @Column(name = "key", nullable = false, unique = true, length = 60)
    private String key;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    /** Type-level gating flag (CONSENT-10) — the sole source of truth ConsentService.outstandingConsents() reads for mandatoriness. */
    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    /** Lifecycle flag (CONSENT-11) — deactivation is always this dedicated column; the inherited BaseEntity soft-delete mechanism is never used for it (D-02). */
    @Column(name = "active", nullable = false)
    private boolean active;

    /** True only for the four seeded REG-02 types; only SUPADM may deactivate or un-mandate them (D-07). */
    @Column(name = "system_defined", nullable = false)
    private boolean systemDefined;
}
