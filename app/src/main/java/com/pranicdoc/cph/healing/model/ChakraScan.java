package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.pranicdoc.cph.common.enums.ScanType;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;

/**
 * ChakraScan — pre or post healing chakra scan.
 * Each session has exactly 2 scans (PRE + POST).
 * Each scan contains 25+ ChakraReading entries.
 */
@Entity
@Table(name = "chakra_scan")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ChakraScan extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_session_id", nullable = false)
    @JsonBackReference
    private HealingSession healingSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false, length = 10)
    private ScanType scanType; // PRE or POST

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "chakraScan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference
    private List<ChakraReading> readings = new ArrayList<>();
}
