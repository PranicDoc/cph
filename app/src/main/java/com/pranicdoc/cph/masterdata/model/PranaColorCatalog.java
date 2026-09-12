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
 * PranaColorCatalog — master list of prana colors used in healing protocols.
 * healer_id NULL = system-level; non-null = healer-custom entry.
 */
@Entity
@Table(name = "prana_color_catalog")
@SQLRestriction("is_deleted = false")
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

    @ElementCollection(fetch = FetchType.LAZY)
    @Builder.Default
    private List<String> properties = new ArrayList<>(); // Individual healing properties of this color

    @ElementCollection(fetch = FetchType.LAZY)
    @Builder.Default
    private List<String> applications = new ArrayList<>(); // Individual usage applications of this color

    @Column(name = "healer_id", columnDefinition = "uuid")
    private UUID healerId; // NULL = system-level, non-null = healer-custom

    @Column(name = "description", length = 500)
    private String description;
}
