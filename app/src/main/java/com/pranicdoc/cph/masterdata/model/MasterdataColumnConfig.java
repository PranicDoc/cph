package com.pranicdoc.cph.masterdata.model;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

/**
 * MasterdataColumnConfig — the DB-backed column-config registry (Phase 01,
 * masterdata-import-redesign). One row per importable spreadsheet column per
 * {@link MasterdataType}; Phase 02's {@code MasterDataImporter}
 * implementations read this table at runtime to learn which columns to
 * extract/require per type, replacing today's hardcoded column constants in
 * {@code ProtocolImportService}.
 *
 * <p>Mirrors {@code ConsentType}'s shape (system-defined seed rows, admin
 * extensible), with two deliberate differences:
 * <ul>
 *   <li>No {@code active} column — MDIMPORT-03 requires real removal, so
 *       this entity uses {@link BaseEntity}'s inherited
 *       {@link BaseEntity#softDelete()} / {@code is_deleted} instead
 *       (Pitfall 1 — do not copy {@code ConsentType}'s no-removal carve-out
 *       here).</li>
 *   <li>No {@code @SQLRestriction} re-declaration — inherited from
 *       {@link BaseEntity} at the mapped-superclass level.</li>
 * </ul>
 *
 * <p>D-05: schema stores column/header name, target field, required flag,
 * and data type only — deliberately no transform column. D-06: strictly
 * column-level; no grouping/aggregation/row-level business logic field.
 */
@Entity
@Table(name = "masterdata_column_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class MasterdataColumnConfig extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "masterdata_type", nullable = false, length = 40)
    private MasterdataType masterdataType;

    @Column(name = "column_name", nullable = false, length = 120)
    private String columnName;

    @Column(name = "target_field", nullable = false, length = 60)
    private String targetField;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private ColumnDataType dataType;

    @Column(name = "system_defined", nullable = false)
    private boolean systemDefined;
}
