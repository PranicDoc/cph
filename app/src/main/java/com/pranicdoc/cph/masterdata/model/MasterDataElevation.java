package com.pranicdoc.cph.masterdata.model;

import com.pranicdoc.cph.common.model.BaseEntity;
import com.pranicdoc.cph.rbac.model.Resource;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.UUID;

/**
 * One "unlock shared entries" of a master-data type: until {@code expiresAt} the healer may edit
 * that type's system rows (see {@code MasterDataElevationService}).
 */
@Entity
@Table(name = "master_data_elevation")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class MasterDataElevation extends BaseEntity {

    @Column(name = "healer_id", nullable = false, columnDefinition = "uuid")
    private UUID healerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource", nullable = false, length = 40)
    private Resource resource;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
