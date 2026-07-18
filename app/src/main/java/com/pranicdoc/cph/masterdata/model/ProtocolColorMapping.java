package com.pranicdoc.cph.masterdata.model;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.UUID;

@Entity
@Table(name = "protocol_color_mapping",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"protocol_name", "color_id", "usage_type", "healer_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ProtocolColorMapping extends BaseEntity {

    @Column(name = "protocol_name", nullable = false)
    private String protocolName;

    @Column(name = "color_id", nullable = false)
    private UUID colorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 20)
    private ColorUsageType usageType;

    /** NULL = system-wide; non-null = healer-specific override */
    @Column(name = "healer_id")
    private UUID healerId;

    @Column(name = "chakra_name")
    private String chakraName;
}
