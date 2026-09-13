package com.pranicdoc.cph.common.enums;

import java.util.Locale;

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
    PROTOCOL_COLOR_MAPPING;

    /**
     * Parses the lowercase request-facing word for this type (the {@code
     * /api/v1/masterdata/import/{type}} path segment) into the enum
     * constant. This is the single place that owns the wire-format ↔
     * enum-constant mapping — {@code MasterDataImportController} and {@code
     * RbacAuthz.hasImportPermission} both call this rather than each
     * maintaining their own copy.
     *
     * @throws IllegalArgumentException for any word not in the fixed set —
     *     deliberately allowed to propagate uncaught (handled by {@code
     *     GlobalExceptionHandler} as a 400) rather than degraded to a safe
     *     default, so an unrecognized type never silently resolves to some
     *     other type's importer or permission.
     */
    public static MasterdataType fromParam(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "protocol" -> PROTOCOL;
            case "color" -> PRANA_COLOR;
            case "chakra" -> CHAKRA;
            case "mapping" -> PROTOCOL_COLOR_MAPPING;
            default -> throw new IllegalArgumentException("Unknown masterdata type '" + raw + "'");
        };
    }
}
