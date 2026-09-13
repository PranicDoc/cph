package com.pranicdoc.cph.masterdata.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ProtocolChakraColorUsage — records, per protocol and chakra, which prana
 * colors are used to cleanse and which to energize that chakra
 * (MDIMPORT-22). One row per (protocolName, chakraId, healerId).
 *
 * <p><b>Deliberately NOT built on {@link ProtocolColorMapping}:</b> that
 * entity's unique constraint is {@code (protocol_name, color_id, usage_type,
 * healer_id)} — it has no notion of chakra at all, so the same
 * protocol+color+usage-type applied to two different chakras (a real,
 * common shape in the source data) would collide on that constraint.
 * {@code ProtocolColorMapping}/{@code ProtocolColorMappingImportService}
 * are untouched and keep serving the existing {@code type=mapping} import
 * path exactly as before; this is a separate table for a separate,
 * chakra-centric shape of data.
 *
 * <p>{@code chakraId} is a real FK to {@link ChakraCatalog} (resolved, or
 * auto-created, by chakra name at import time) — unlike {@code
 * ProtocolColorMapping.chakraName}, which is free text. {@code
 * protocolName} stays a plain string, not an FK, matching {@code
 * ProtocolColorMapping.protocolName}'s existing precedent: protocols are
 * matched by name only (case-insensitive), never resolved to a specific
 * {@code ProtocolCatalog} row (a protocol can legitimately span multiple
 * catalog rows across pages under the same name).
 *
 * <p>{@code cleansingColorIds}/{@code energizingColorIds} are {@code
 * @ElementCollection List<UUID>} — mirrors {@link
 * PranaColorCatalog#getProperties()}/{@link PranaColorCatalog#getApplications()}'s
 * exact shape in this same package, so multiple colors per usage stay
 * individually queryable (not a comma-joined string) without inventing a
 * new persistence idiom for this codebase.
 */
@Entity
@Table(name = "protocol_chakra_color_usage",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"protocol_name", "chakra_id", "healer_id"}))
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ProtocolChakraColorUsage extends BaseEntity {

    @Column(name = "protocol_name", nullable = false)
    private String protocolName;

    @Column(name = "chakra_id", nullable = false)
    private UUID chakraId;

    @ElementCollection(fetch = FetchType.LAZY)
    @Builder.Default
    private List<UUID> cleansingColorIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @Builder.Default
    private List<UUID> energizingColorIds = new ArrayList<>();

    /** NULL = system-wide; non-null = healer-specific override */
    @Column(name = "healer_id")
    private UUID healerId;
}
