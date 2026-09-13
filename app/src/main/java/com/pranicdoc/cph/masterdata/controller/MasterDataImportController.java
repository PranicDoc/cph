package com.pranicdoc.cph.masterdata.controller;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.importer.MasterDataImportRegistry;
import com.pranicdoc.cph.rbac.RbacAuthz;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.method.P;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * MasterDataImportController — the unified {@code POST /api/v1/masterdata/import/{type}}
 * endpoint (MDIMPORT-07, Phase 02), dispatching by the {@code type} path
 * segment through {@link MasterDataImportRegistry} to the matching {@code
 * MasterDataImporter}.
 *
 * <p><b>Thin controller (2026-09-13):</b> this class owns request plumbing
 * only — the path variable, the multipart file, and dispatch to the
 * registry. It does not know about {@code Resource}, does not hold a
 * type-to-resource map, and does not parse the raw {@code type} word itself.
 * All of that moved to where it belongs:
 * <ul>
 *   <li>{@code type} word ↔ {@link MasterdataType} constant —
 *       {@link MasterdataType#fromParam(String)}</li>
 *   <li>{@code type} ↔ RBAC {@code Resource} + the permission decision —
 *       {@link RbacAuthz#hasImportPermission(String, String)}</li>
 * </ul>
 *
 * <p><b>D-12 revised:</b> {@code @PreAuthorize} CAN reference a method
 * parameter via SpEL (bound at request time, not compile time) — the
 * earlier "structurally impossible" framing for a static annotation here
 * was wrong. {@code type} carries an explicit {@link P @P("type")} so the
 * binding does not depend on the project compiling with {@code -parameters}.
 * Proven behaviorally (not by reflection, since the literal carries a
 * bean-method call rather than a bare {@code hasPermission(...)} pin) by
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
    private final SecurityContextUtil securityContextUtil;

    @PostMapping(value = "/import/{type}", consumes = "multipart/form-data")
    @PreAuthorize("@rbacAuthz.hasImportPermission(#type, 'CREATE')")
    public ResponseEntity<ApiResponse<ImportResult>> importMasterData(
            @PathVariable @P("type") String type, @RequestPart("file") MultipartFile file) {
        MasterdataType kind = MasterdataType.fromParam(type);
        // PD-40-04: SUPADM-only, non-grantable system-row override — same pair
        // MasterDataController's update/delete handlers resolve, so importers can
        // apply the identical ownership guard MasterDataService's CRUD path enforces.
        boolean isSuperAdmin = rbacAuthz.hasImportPermission(type, "ADMIN");
        return ResponseEntity.ok(new ApiResponse<>(true, "Import complete",
                registry.get(kind).importFile(file, isSuperAdmin, securityContextUtil.getCurrentHealerId())));
    }
}
