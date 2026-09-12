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
 * <p><b>D-10 (critical):</b> all 4 verbs — READ (list), CREATE, UPDATE,
 * DELETE — are normal grantable RBAC permissions on {@code
 * Resource.MASTER_DATA_COLUMN_CONFIG}. Each handler below is gated on ITS
 * OWN matching verb: {@code READ} for GET, {@code CREATE} for POST, {@code
 * UPDATE} for PUT, {@code DELETE} for DELETE. There is no shared/blanket
 * {@code ADMIN}-verb gate anywhere on this controller — unlike {@code
 * MasterDataController.updateChakra}'s non-grantable-override idiom, every
 * caller reaching {@link MasterdataColumnConfigService} is already gated on
 * the exact verb their action requires, so there is no in-body override
 * call and no {@code RbacAuthz} field/constructor injection here.
 *
 * <p>SUPADM passes every gate via the single hardcoded short-circuit inside
 * {@code RbacAuthz}; a custom role CAN be granted any of the 4 verbs
 * independently (e.g. a role holding only READ passes GET but is rejected
 * on POST/PUT/DELETE).
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
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_COLUMN_CONFIG','READ')")
    public ResponseEntity<ApiResponse<List<ColumnConfigResponse>>> list(@RequestParam MasterdataType type) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterdataColumnConfigService.list(type)));
    }

    @PostMapping
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_COLUMN_CONFIG','CREATE')")
    public ResponseEntity<ApiResponse<ColumnConfigResponse>> create(@Valid @RequestBody ColumnConfigCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Column config created", masterdataColumnConfigService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_COLUMN_CONFIG','UPDATE')")
    public ResponseEntity<ApiResponse<ColumnConfigResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody ColumnConfigUpdateRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Column config updated", masterdataColumnConfigService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_COLUMN_CONFIG','DELETE')")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id) {
        masterdataColumnConfigService.remove(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Column config deleted", null));
    }
}
