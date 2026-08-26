package com.pranicdoc.cph.masterdata.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * ChakraCatalog — master list of chakras (14 major + 11 minor + healer-custom).
 * healer_id NULL = system-level; non-null = healer-custom entry.
 */
@Entity
@Table(name = "chakra_catalog")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ChakraCatalog extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code; // e.g. "CROWN", "AJNA", "SOLAR_PLEXUS"

    @Column(name = "name", nullable = false)
    private String name; // e.g. "Crown Chakra"

    @Column(name = "category")
    private String category; // "major" or "minor"

    @Column(name = "location")
    private String location; // Body location description

    @Column(name = "healer_id", columnDefinition = "uuid")
    private UUID healerId; // NULL = system-level, non-null = healer-custom
}
