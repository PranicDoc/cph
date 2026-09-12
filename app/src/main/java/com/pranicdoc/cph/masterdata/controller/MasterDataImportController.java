package com.pranicdoc.cph.masterdata.controller;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.importer.MasterDataImportRegistry;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.model.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
 * <p><b>Why a separate controller from {@link MasterDataController} (D-18):</b>
 * this endpoint's RBAC gate is a runtime, in-body {@code rbacAuthz.hasPermission(...)}
 * call (D-12) — the resource being checked depends on the request's {@code type}
 * param and is not known at compile time, so a static {@code @PreAuthorize}
 * annotation is structurally impossible here. {@code MasterDataController}'s
 * endpoint/gate counts are pinned by two build-gate tests
 * ({@code LegacyEndpointCutoverSweepTest}, {@code MasterDataControllerAuthorizationContractTest})
 * at an exact count; adding an ungated-by-annotation handler to that controller
 * would collide with both pins. This mirrors the exact precedent Phase 1 already
 * set for {@link MasterdataColumnConfigController}, registered in
 * {@code LegacyEndpointCutoverSweepTest.DELIBERATELY_UNSWEPT} for the same
 * "added after the sweep was frozen" reason — though that controller's gates
 * ARE bare {@code @PreAuthorize} annotations, simply outside the frozen counts,
 * whereas this controller's single handler has no annotation to reflect on at
 * all. Its RBAC behavior is instead proven behaviorally by
 * {@code MasterDataImportControllerAuthorizationTest}.
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

    /**
     * D-12 critical ordering, never reordered: (1) parse {@code type} — an
     * unknown word throws {@link IllegalArgumentException} (400 via {@code
     * GlobalExceptionHandler}) before any RBAC lookup ever runs; (2) resolve the
     * resource from the fixed {@link #TYPE_TO_RESOURCE} map — never {@code
     * Resource.valueOf(rawUserString)} directly; (3) check permission — a denial
     * throws {@link AccessDeniedException} (403); (4) only then dispatch to
     * {@link MasterDataImportRegistry#get(MasterdataType)}. No {@code
     * @PreAuthorize} anywhere on this handler — structurally impossible per
     * D-12, since the resource is not known until {@code type} is parsed.
     */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ImportResult>> importMasterData(
            @RequestParam String type, @RequestPart("file") MultipartFile file) {
        MasterdataType kind = parseType(type);
        Resource resource = TYPE_TO_RESOURCE.get(kind);
        if (!rbacAuthz.hasPermission(resource.name(), "CREATE")) {
            throw new AccessDeniedException("Not permitted to import " + kind);
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Import complete", registry.get(kind).importFile(file)));
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
