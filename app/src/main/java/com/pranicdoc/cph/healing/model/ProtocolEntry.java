package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ProtocolEntry — treatment protocol per chakra/organ in a session.
 * Each entry can have multiple cleansing/energising colors.
 */
@Entity
@Table(name = "protocol_entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ProtocolEntry extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_session_id", nullable = false)
    @JsonBackReference
    private HealingSession healingSession;

    @Column(name = "chakra_id")
    private UUID chakraId; // FK to ChakraCatalog (nullable — could be organ-based)

    @Column(name = "target_area")
    private String targetArea; // e.g., "Solar Plexus Chakra" or specific organ

    @Column(name = "technique")
    private String technique; // Healing technique applied

    @Column(name = "notes")
    private String notes;

    @Column(name = "protocol_name")
    private String protocolName;

    @Column(name = "reference_book")
    private String referenceBook;

    @Column(columnDefinition = "TEXT", name = "detailed_protocol")
    private String detailedProtocol;

    @OneToMany(mappedBy = "protocolEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference
    private List<ProtocolColor> colors = new ArrayList<>();
}
