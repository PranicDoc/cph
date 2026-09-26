package com.pranicdoc.cph.masterdata.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * ProtocolChakra — many-to-many junction between {@link ProtocolCatalog} and
 * {@link ChakraCatalog}: which chakras a protocol addresses.
 * UNIQUE(protocol_id, chakra_id, healer_id) prevents duplicate links per owner.
 *
 * <p>{@code healerId} follows the same nullable "system vs custom" pattern as
 * {@link ChakraCatalog}/{@link ProtocolCatalog}: NULL = system-level (bulk
 * import), non-null = a specific healer's own private addition, visible only
 * to them (see {@code MasterDataService#getChakraColorGroupsForProtocol}).
 * {@link ProtocolChakraColor} rows carry no {@code healerId} of their own —
 * their ownership is whatever this row's {@code healerId} is, since a color
 * row is always created as this row's child.
 *
 * <p>Deliberately unidirectional: neither {@code ProtocolCatalog} nor
 * {@code ChakraCatalog} carries a back-reference collection to this table —
 * both catalogs stay independent, matching the project's existing
 * cross-aggregate convention of referencing a catalog row by real FK from
 * the owning side only (see {@link ProtocolColorMapping}, {@code
 * ProtocolColor} in the healing package).
 */
@Entity
@Table(name = "protocol_chakra",
        uniqueConstraints = @UniqueConstraint(columnNames = {"protocol_id", "chakra_id", "healer_id"}))
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ProtocolChakra extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", nullable = false)
    private ProtocolCatalog protocol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chakra_id", nullable = false)
    private ChakraCatalog chakra;

    @Column(name = "healer_id", columnDefinition = "uuid")
    private UUID healerId; // NULL = system-level, non-null = healer-custom
}
