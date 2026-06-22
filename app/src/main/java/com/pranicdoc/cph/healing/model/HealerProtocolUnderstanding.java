package com.pranicdoc.cph.healing.model;

import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "healer_protocol_understanding")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class HealerProtocolUnderstanding extends TenantEntity {

    @Column(name = "healing_case_id", nullable = false, unique = true)
    private UUID healingCaseId;

    @Column(name = "healer_id", nullable = false)
    private UUID healerId;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Column(name = "protocol_name")
    private String protocolName;

    @Column(name = "cleansing_colors_json", columnDefinition = "TEXT")
    private String cleansingColorsJson;

    @Column(name = "energising_colors_json", columnDefinition = "TEXT")
    private String energisingColorsJson;

    @Column(name = "chakra_readings_json", columnDefinition = "TEXT")
    private String chakraReadingsJson;
}
