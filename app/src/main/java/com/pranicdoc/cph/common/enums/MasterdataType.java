package com.pranicdoc.cph.common.enums;

/**
 * MasterdataType — the fixed set of masterdata catalogs that support the
 * column-config import registry (D-07). Adding a new type requires a code
 * deploy (a new constant here + a new {@code MasterDataImporter} bean in
 * Phase 02) — only the columns *within* an existing type are runtime-editable
 * via the DB-backed {@code masterdata_column_config} table.
 *
 * <p>Lives in {@code common/enums} rather than {@code masterdata/model} so
 * Phase 02's importer/registry classes can reference it without introducing
 * a package cycle.
 */
public enum MasterdataType {
    PROTOCOL,
    PRANA_COLOR,
    CHAKRA,
    PROTOCOL_COLOR_MAPPING
}
