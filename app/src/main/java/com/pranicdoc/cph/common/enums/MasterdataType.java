package com.pranicdoc.cph.common.enums;

import java.util.Locale;

/**
 * MasterdataType — the fixed set of masterdata catalogs importable through
 * the unified {@code POST /api/v1/masterdata/import/{type}} endpoint.
 * Adding a new type, or changing the columns an existing type's importer
 * reads, is a code change (a new constant here plus a new/updated {@code
 * MasterDataImporter} bean) — there is no DB-backed column registry; each
 * importer's spreadsheet columns are a fixed Java template.
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
