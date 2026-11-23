package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pranicdoc.cph.common.encryption.EncryptedStringConverter;
import com.pranicdoc.cph.common.enums.DurationUnit;
import com.pranicdoc.cph.common.enums.Onset;
import com.pranicdoc.cph.common.enums.Timing;
import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

/**
 * One problem of the case's chief complaint, how long it has been going on, and the history of its
 * presenting illness. A row without a duration is a legacy one migrated from the old free-text complaint.
 */
@Entity
@Table(name = "case_chief_complaint")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class CaseChiefComplaint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_case_id", nullable = false)
    @JsonBackReference
    private HealingCase healingCase;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "problem", nullable = false, columnDefinition = "TEXT")
    private String problem; // 🔒

    @Column(name = "duration_value")
    private Integer durationValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_unit", length = 10)
    private DurationUnit durationUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "onset", length = 10)
    private Onset onset;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "location", columnDefinition = "TEXT")
    private String location; // 🔒

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "character_text", columnDefinition = "TEXT")
    private String characterText; // 🔒

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "aggravating_factor", columnDefinition = "TEXT")
    private String aggravatingFactor; // 🔒

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "relieving_factor", columnDefinition = "TEXT")
    private String relievingFactor; // 🔒

    @Enumerated(EnumType.STRING)
    @Column(name = "timing", length = 15)
    private Timing timing;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "timing_pattern", columnDefinition = "TEXT")
    private String timingPattern; // 🔒

    /** True for a row migrated from the old free-text complaint: shown as saved, completed on the next edit. */
    @JsonProperty("legacy")
    public boolean isLegacy() {
        return durationValue == null || durationUnit == null;
    }

    /** "Headache since 3 months"; a legacy row reads as the problem alone. */
    public String line() {
        return isLegacy() ? problem : problem + " since " + durationUnit.format(durationValue);
    }
}
