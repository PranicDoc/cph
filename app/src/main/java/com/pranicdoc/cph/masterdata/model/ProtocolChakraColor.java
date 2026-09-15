package com.pranicdoc.cph.masterdata.model;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

/**
 * ProtocolChakraColor — the prana color(s) used to CLEANSE or ENERGISE a
 * specific protocol+chakra combination (not the protocol or chakra alone).
 * UNIQUE(protocol_chakra_id, color_id, usage_type) — a color may legitimately
 * appear once per usage type for the same combination (e.g. the same color
 * used for both cleansing and energizing), but never as an exact duplicate row.
 *
 * <p>Keyed off {@link ProtocolChakra} (the resolved protocol+chakra pair)
 * rather than duplicating protocol_id/chakra_id here — the pair's identity
 * already lives in one place. {@link PranaColorCatalog} gains no reverse
 * mapping; it stays the independent, unidirectionally-referenced source of
 * truth for color definitions (no color data is duplicated onto this row).
 */
@Entity
@Table(name = "protocol_chakra_color",
        uniqueConstraints = @UniqueConstraint(columnNames = {"protocol_chakra_id", "color_id", "usage_type"}))
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ProtocolChakraColor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_chakra_id", nullable = false)
    private ProtocolChakra protocolChakra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id", nullable = false)
    private PranaColorCatalog color;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 15)
    private ColorUsageType usageType;
}
