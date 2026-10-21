package com.pranicdoc.cph.auth.model;

import com.pranicdoc.cph.common.enums.HealingLevel;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.enums.UiVersion;
import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * Healer entity — a practitioner. Post-26-03, Healer is the tenant ROOT
 * (not tenant-scoped) — it no longer extends TenantEntity/carries a
 * healer_id column of its own.
 */
@Entity
@Table(name = "healer")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class Healer extends BaseEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /**
     * Stores the sub value provided by the Google as the emails may change but this sub wont.
     * NULL means "not linked" — providerUserId != null is the sole source of truth for
     * Google account link state (authProvider is left untouched per 21-google-oauth-linking D-09).
     */
    @Column(name = "provider_user_id")
    private String providerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "healing_level", length = 20)
    private HealingLevel healingLevel;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** The web UI this user chose in the profile menu; follows them across devices. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ui_version", nullable = false, length = 10)
    @Builder.Default
    private UiVersion uiVersion = UiVersion.CLASSIC;

    enum AuthProvider{
        LOCAL, GOOGLE
    }
}
