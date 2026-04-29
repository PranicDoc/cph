package com.pranicdoc.cph.masterdata.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * PranaColorCatalog — master list of prana colors used in healing protocols.
 * clinic_id NULL = system-level; non-null = clinic-custom entry.
 */
@Entity
@Table(name = "prana_color_catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class PranaColorCatalog extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code; // e.g. "WHITE", "GREEN", "VIOLET"

    @Column(name = "name", nullable = false)
    private String name; // e.g. "White Prana"

    @Column(name = "color_hex", length = 7)
    private String colorHex; // e.g. "#FFFFFF"

    @Column(name = "properties", columnDefinition = "TEXT")
    private String properties; // Description of color healing properties

    @Column(name = "clinic_id", columnDefinition = "uuid")
    private UUID clinicId; // NULL = system-level, non-null = clinic-custom
}
