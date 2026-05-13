package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * ChakraReading — individual chakra measurement within a scan.
 * UNIQUE(scan_id, chakra_id) — one reading per chakra per scan.
 * All 25+ chakra readings are recorded (no sparse storage).
 */
@Entity
@Table(name = "chakra_reading",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chakra_scan_id", "chakra_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ChakraReading extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chakra_scan_id", nullable = false)
    @JsonBackReference
    private ChakraScan chakraScan;

    @Column(name = "chakra_id", nullable = false)
    private UUID chakraId; // FK to ChakraCatalog

    @Column(name = "chakra_name")
    private String chakraName;

    @Column(name = "energy_level")
    private String energyLevel; // "Normal", "Congested", "Depleted"

    @Column(name = "activation_level")
    private String activationLevel; // "Underactivated", "Overactivated", "Normal"

    @Column(name = "notes")
    private String notes;
}
