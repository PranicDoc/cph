package com.pranicdoc.cph.masterdata.controller;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ColumnConfigCreateRequest;
import com.pranicdoc.cph.masterdata.dto.ColumnConfigResponse;
import com.pranicdoc.cph.masterdata.dto.ColumnConfigUpdateRequest;
import com.pranicdoc.cph.masterdata.service.MasterdataColumnConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * MasterdataColumnConfigController — REST surface for the {@code
 * masterdata_column_config} registry (Phase 01, masterdata-import-redesign,
 * MDIMPORT-01/02/03).
 *
 * <p><b>D-10 superseded (2026-09-13):</b> this registry is SUPADM-only.
 * Every handler carries a bare {@code @PreAuthorize("hasRole('SUPADM')")} —
 * the same literal and idiom as {@code BootstrapController.assignRole}.
 * There is no {@code Resource.MASTER_DATA_COLUMN_CONFIG} entry (removed);
 * this is not a grantable permission and no custom role can ever hold it.
 *
 * <p>Follows the {@code ApiResponse<T>} wrapper convention used by every
 * sibling endpoint under {@code /api/v1/masterdata} ({@link
 * MasterDataController}), not {@code ConsentTypeController}'s bare {@code
 * ResponseEntity<T>} style, since both live under the same base path and
 * are consumed by the same admin UI page.
 */
@RestController
@RequestMapping("/api/v1/masterdata/column-config")
@RequiredArgsConstructor
public class MasterdataColumnConfigController {

    private final MasterdataColumnConfigService masterdataColumnConfigService;

    @GetMapping
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<ApiResponse<List<ColumnConfigResponse>>> list(@RequestParam MasterdataType type) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterdataColumnConfigService.list(type)));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<ApiResponse<ColumnConfigResponse>> create(@Valid @RequestBody ColumnConfigCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Column config created", masterdataColumnConfigService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<ApiResponse<ColumnConfigResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody ColumnConfigUpdateRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Column config updated", masterdataColumnConfigService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id) {
        masterdataColumnConfigService.remove(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Column config deleted", null));
    }
}
