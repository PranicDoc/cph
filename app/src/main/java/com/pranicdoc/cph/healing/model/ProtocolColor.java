package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * ProtocolColor — prana color used in a protocol entry.
 * Single table with usage_type enum (CLEANSING/ENERGISING).
 * UNIQUE(entry_id, color_id, usage_type).
 */
@Entity
@Table(name = "protocol_color",
        uniqueConstraints = @UniqueConstraint(columnNames = {"protocol_entry_id", "color_id", "usage_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ProtocolColor extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_entry_id", nullable = false)
    @JsonBackReference
    private ProtocolEntry protocolEntry;

    @Column(name = "color_id", nullable = false)
    private UUID colorId; // FK to PranaColorCatalog

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 15)
    private ColorUsageType usageType; // CLEANSING or ENERGISING
}
