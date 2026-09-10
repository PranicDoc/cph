package com.pranicdoc.cph.healing.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.time.Instant;

/**
 * Declaration (consent) — gates access to patient data for the case.
 * Must be signed before any session data can be accessed.
 */
@Entity
@Table(name = "declaration")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class Declaration extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_case_id", nullable = false, unique = true)
    private HealingCase healingCase;

    @Column(name = "patient_consent", nullable = false)
    private boolean patientConsent;

    @Column(name = "healer_declaration", nullable = false)
    private boolean healerDeclaration;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "patient_signature")
    private String patientSignature;

    @Column(name = "healer_signature")
    private String healerSignature;
}
