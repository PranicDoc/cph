package com.pranicdoc.cph.masterdata.controller;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.importer.MasterDataImportRegistry;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.model.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.method.P;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;

/**
 * MasterDataImportController — the unified {@code POST /api/v1/masterdata/import}
 * endpoint (MDIMPORT-07, Phase 02), dispatching by request-supplied {@code type}
 * through {@link MasterDataImportRegistry} to the matching {@code MasterDataImporter}.
 *
 * <p><b>D-12 revised (2026-09-13):</b> the gate is now a declarative {@code
 * @PreAuthorize} that evaluates {@link #hasImportPermission(String, String)}
 * against the request's {@code #type} parameter — {@code @PreAuthorize} CAN
 * reference a method parameter via SpEL (bound at request time, not compile
 * time), so the earlier "structurally impossible" framing was wrong. {@code
 * type} carries an explicit {@link P @P("type")} so the binding does not
 * depend on the project compiling with {@code -parameters}. {@code
 * hasImportPermission} still does exactly D-12's original three steps in
 * order — parse (unknown word throws {@link IllegalArgumentException}, 400
 * via {@code GlobalExceptionHandler}, before any RBAC lookup) → resolve the
 * resource from the fixed {@link #TYPE_TO_RESOURCE} map → check permission
 * — it is just invoked from the annotation instead of the method body.
 * Denial is Spring Security's own {@code AccessDeniedException} (403) from
 * the {@code @PreAuthorize} expression evaluating {@code false}, not a
 * hand-thrown one. Proven behaviorally (not by reflection, since the literal
 * carries a bean-method call rather than a bare {@code hasPermission(...)}
 * pin) by {@code MasterDataImportControllerAuthorizationTest} — the same
 * suite that proved the old in-body version, unchanged, still passes.
 *
 * <p>Follows the {@code ApiResponse<T>} wrapper convention shared by every
 * sibling endpoint under {@code /api/v1/masterdata}.
 */
@RestController
@RequestMapping("/api/v1/masterdata")
@RequiredArgsConstructor
public class MasterDataImportController {

    private final MasterDataImportRegistry registry;
    private final RbacAuthz rbacAuthz;
    private final SecurityContextUtil securityContextUtil;

    /**
     * D-13: fixed, exhaustive, compile-time type-to-resource map. {@code
     * PROTOCOL_COLOR_MAPPING} deliberately reuses {@code MASTER_DATA_PROTOCOLS}
     * (no dedicated resource exists for it) — consistent with how its existing
     * CRUD endpoints are already gated on {@link MasterDataController}.
     */
    private static final Map<MasterdataType, Resource> TYPE_TO_RESOURCE = Map.of(
            MasterdataType.PROTOCOL, Resource.MASTER_DATA_PROTOCOLS,
            MasterdataType.PRANA_COLOR, Resource.MASTER_DATA_COLORS,
            MasterdataType.CHAKRA, Resource.MASTER_DATA_CHAKRAS,
            MasterdataType.PROTOCOL_COLOR_MAPPING, Resource.MASTER_DATA_PROTOCOLS);

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("@masterDataImportController.hasImportPermission(#type, 'CREATE')")
    public ResponseEntity<ApiResponse<ImportResult>> importMasterData(
            @RequestParam @P("type") String type, @RequestPart("file") MultipartFile file) {
        MasterdataType kind = parseType(type);
        Resource resource = TYPE_TO_RESOURCE.get(kind);
        // PD-40-04: SUPADM-only, non-grantable system-row override — same pair
        // MasterDataController's update/delete handlers resolve, so importers can
        // apply the identical ownership guard MasterDataService's CRUD path enforces.
        boolean isSuperAdmin = rbacAuthz.hasPermission(resource.name(), "ADMIN");
        return ResponseEntity.ok(new ApiResponse<>(true, "Import complete",
                registry.get(kind).importFile(file, isSuperAdmin, securityContextUtil.getCurrentHealerId())));
    }

    /**
     * Called from {@code importMasterData}'s {@code @PreAuthorize} SpEL, not
     * from application code — must stay public and unannotated (no {@code
     * @PreAuthorize} of its own) so Spring Security's bean-method SpEL call
     * is a plain, unadvised invocation. D-12's original three-step order:
     * parse {@code type} (unknown word throws {@link IllegalArgumentException},
     * 400 via {@code GlobalExceptionHandler}, before any RBAC lookup) → resolve
     * the resource from {@link #TYPE_TO_RESOURCE} (never {@code
     * Resource.valueOf(rawUserString)} directly) → check permission.
     */
    public boolean hasImportPermission(String type, String verb) {
        MasterdataType kind = parseType(type);
        Resource resource = TYPE_TO_RESOURCE.get(kind);
        return rbacAuthz.hasPermission(resource.name(), verb);
    }

    private MasterdataType parseType(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "protocol" -> MasterdataType.PROTOCOL;
            case "color" -> MasterdataType.PRANA_COLOR;
            case "chakra" -> MasterdataType.CHAKRA;
            case "mapping" -> MasterdataType.PROTOCOL_COLOR_MAPPING;
            default -> throw new IllegalArgumentException("Unknown masterdata type '" + raw + "'");
        };
    }
}
