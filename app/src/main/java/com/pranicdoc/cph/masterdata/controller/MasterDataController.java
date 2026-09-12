package com.pranicdoc.cph.masterdata.controller;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.dto.ChakraRequest;
import com.pranicdoc.cph.masterdata.dto.PranaColorRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolColorMappingRequest;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.dto.ProtocolRequest;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import com.pranicdoc.cph.masterdata.service.MasterDataService;
import com.pranicdoc.cph.masterdata.service.ProtocolImportService;
import com.pranicdoc.cph.rbac.RbacAuthz;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * MasterDataController — the chakra, prana-colour and protocol catalog surface
 * (<code>/api/v1/masterdata</code>).
 *
 * <p>Every endpoint on this controller is gated on the dynamic, DB-backed
 * permission model via a bare method-security expression of the form
 * {@code rbacAuthz.hasPermission('RESOURCE', 'VERB')} — there is no
 * {@code hasRole}/{@code hasAnyRole} prefix and no boolean AND with a coarse
 * role check (Phase 40 D-02). SUPADM passes every gate through the single
 * hardcoded short-circuit inside
 * {@link com.pranicdoc.cph.rbac.RbacAuthz}; every other caller is resolved
 * against their granted {@code MASTER_DATA_{CHAKRAS,COLORS,PROTOCOLS}} verbs.
 *
 * <p><b>D-09:</b> the catalog READ endpoints (list/get chakras, colours,
 * protocols by id or code) were open to <em>any</em> authenticated caller
 * before this phase, which leaked internal catalog keys and system-row
 * structure to every logged-in user. They are now gated on
 * {@code MASTER_DATA_*:READ}; master-data reads become a grantable permission
 * onboarded healers receive.
 *
 * <p><b>D-04 / PD-40-04:</b> the seven mutating handlers still pass a
 * system-row override boolean to {@link MasterDataService}. Its source moved
 * from a hardcoded top-level-role string comparison to
 * {@code rbacAuthz.hasPermission(MASTER_DATA_*, ADMIN)}; see the comment
 * block on {@link #updateChakra} for why that pair is intentionally
 * SUPADM-only and non-grantable.
 */
@RestController
@RequestMapping("/api/v1/masterdata")
@RequiredArgsConstructor
public class MasterDataController {

    private final MasterDataService masterDataService;
    private final ProtocolImportService protocolImportService;
    private final RbacAuthz rbacAuthz;

    @GetMapping("/chakras")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_CHAKRAS','READ')")
    public ResponseEntity<ApiResponse<List<ChakraCatalog>>> getChakras(
            @RequestParam(required = false) UUID healerId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getAllChakras(healerId)));
    }

    @GetMapping("/chakras/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_CHAKRAS','READ')")
    public ResponseEntity<ApiResponse<ChakraCatalog>> getChakraById(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getChakraById(id)));
    }

    @GetMapping("/chakras/code/{code}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_CHAKRAS','READ')")
    public ResponseEntity<ApiResponse<ChakraCatalog>> getChakraByCode(@PathVariable String code) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getChakraByCode(code)));
    }

    @PostMapping("/chakras")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_CHAKRAS','CREATE')")
    public ResponseEntity<ApiResponse<ChakraCatalog>> createChakra(@Valid @RequestBody ChakraRequest req) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Chakra created", masterDataService.createChakra(req)));
    }

    @PutMapping("/chakras/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_CHAKRAS','UPDATE')")
    public ResponseEntity<ApiResponse<ChakraCatalog>> updateChakra(
            @PathVariable UUID id, @Valid @RequestBody ChakraRequest req) {
        // PD-40-04 (shared with the six other master-data mutating handlers): the system-row
        // override below is intentionally SUPADM-only and NON-GRANTABLE. The three MASTER_DATA_*
        // resources deliberately do not allow-list the ADMIN verb in Resource.java, so
        // RolePermissionLookupService can never resolve a MASTER_DATA_*+ADMIN grant to true
        // (Permission's constructor rejects that pair at role-creation time). Net effect:
        // SUPADM -> true via the RbacAuthz short-circuit; every other caller -> false, forever.
        // That is exactly the pre-cutover behaviour, which is what D-04 requires ("unchanged").
        // Contrast LEGAL_DOCUMENTS:ADMIN (plan 40-04), which IS allow-listed and therefore a
        // genuinely grantable permission. Resolving 40-RESEARCH OQ4 / A7, this plan chose
        // accept-and-document over adding Operation.ADMIN to the three constants (a Phase-35
        // catalog change with RbacCatalogService / catalog-test / Phase-38 UI fallout for zero
        // behavioural gain this phase). Note: the SC1 sweep and the allow-list validity check
        // apply to @PreAuthorize gate literals, not to this in-body capability call, so this
        // pair is not an unflagged D-10 violation.
        boolean isSuperAdmin = rbacAuthz.hasPermission("MASTER_DATA_CHAKRAS", "ADMIN");
        return ResponseEntity.ok(new ApiResponse<>(true, "Chakra updated", masterDataService.updateChakra(id, req, isSuperAdmin)));
    }

    @DeleteMapping("/chakras/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_CHAKRAS','DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteChakra(@PathVariable UUID id) {
        // PD-40-04: SUPADM-only, non-grantable system-row override — see updateChakra.
        boolean isSuperAdmin = rbacAuthz.hasPermission("MASTER_DATA_CHAKRAS", "ADMIN");
        masterDataService.deleteChakra(id, isSuperAdmin);
        return ResponseEntity.ok(new ApiResponse<>(true, "Chakra deleted", null));
    }

    @GetMapping("/colors")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_COLORS','READ')")
    public ResponseEntity<ApiResponse<List<PranaColorCatalog>>> getColors(
            @RequestParam(required = false) UUID healerId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getAllColors(healerId)));
    }

    @GetMapping("/colors/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_COLORS','READ')")
    public ResponseEntity<ApiResponse<PranaColorCatalog>> getColorById(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getColorById(id)));
    }

    @GetMapping("/colors/code/{code}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_COLORS','READ')")
    public ResponseEntity<ApiResponse<PranaColorCatalog>> getColorByCode(@PathVariable String code) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getColorByCode(code)));
    }

    @PostMapping("/colors")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_COLORS','CREATE')")
    public ResponseEntity<ApiResponse<PranaColorCatalog>> createColor(@Valid @RequestBody PranaColorRequest req) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Color created", masterDataService.createColor(req)));
    }

    @PutMapping("/colors/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_COLORS','UPDATE')")
    public ResponseEntity<ApiResponse<PranaColorCatalog>> updateColor(
            @PathVariable UUID id, @Valid @RequestBody PranaColorRequest req) {
        // PD-40-04: SUPADM-only, non-grantable system-row override — see updateChakra.
        boolean isSuperAdmin = rbacAuthz.hasPermission("MASTER_DATA_COLORS", "ADMIN");
        return ResponseEntity.ok(new ApiResponse<>(true, "Color updated", masterDataService.updateColor(id, req, isSuperAdmin)));
    }

    @DeleteMapping("/colors/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_COLORS','DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteColor(@PathVariable UUID id) {
        // PD-40-04: SUPADM-only, non-grantable system-row override — see updateChakra.
        boolean isSuperAdmin = rbacAuthz.hasPermission("MASTER_DATA_COLORS", "ADMIN");
        masterDataService.deleteColor(id, isSuperAdmin);
        return ResponseEntity.ok(new ApiResponse<>(true, "Color deleted", null));
    }

    // PD-40-03 (resolves 40-RESEARCH OQ3 / A4): the three /protocol-mappings endpoints map to
    // MASTER_DATA_PROTOCOLS, not MASTER_DATA_COLORS. A mapping row is a protocol's colour
    // assignment — created and removed as part of protocol curation — and the URL path is
    // protocol-prefixed. Both resources allow-list the verbs used, so this is a semantic choice
    // about which grant unlocks the capability, not a validity one.
    @GetMapping("/protocol-mappings")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','READ')")
    public ResponseEntity<ApiResponse<List<ProtocolColorMapping>>> getMappings(
            @RequestParam(required = false) UUID healerId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getAllMappings(healerId)));
    }

    @PostMapping("/protocol-mappings")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','CREATE')")
    public ResponseEntity<ApiResponse<List<ProtocolColorMapping>>> createMappings(
            @Valid @RequestBody ProtocolColorMappingRequest req) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Mappings created", masterDataService.createMappings(req)));
    }

    @DeleteMapping("/protocol-mappings/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteMapping(@PathVariable UUID id) {
        // PD-40-04: SUPADM-only, non-grantable system-row override — see updateChakra.
        // Uses MASTER_DATA_PROTOCOLS to stay consistent with the PD-40-03 resource choice.
        boolean isSuperAdmin = rbacAuthz.hasPermission("MASTER_DATA_PROTOCOLS", "ADMIN");
        masterDataService.deleteMapping(id, isSuperAdmin);
        return ResponseEntity.ok(new ApiResponse<>(true, "Mapping deleted", null));
    }

    @GetMapping("/protocols")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','READ')")
    public ResponseEntity<ApiResponse<List<ProtocolCatalog>>> getProtocols(
            @RequestParam(required = false) UUID healerId,
            @RequestParam(required = false) ProtocolType type) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getAllProtocols(healerId, type)));
    }

    @GetMapping("/protocols/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','READ')")
    public ResponseEntity<ApiResponse<ProtocolCatalog>> getProtocolById(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getProtocolById(id)));
    }

    @PostMapping("/protocols")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','CREATE')")
    public ResponseEntity<ApiResponse<ProtocolCatalog>> createProtocol(@Valid @RequestBody ProtocolRequest req) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Protocol created", masterDataService.createProtocol(req)));
    }

    @PutMapping("/protocols/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','UPDATE')")
    public ResponseEntity<ApiResponse<ProtocolCatalog>> updateProtocol(
            @PathVariable UUID id, @Valid @RequestBody ProtocolRequest req) {
        // PD-40-04: SUPADM-only, non-grantable system-row override — see updateChakra.
        boolean isSuperAdmin = rbacAuthz.hasPermission("MASTER_DATA_PROTOCOLS", "ADMIN");
        return ResponseEntity.ok(new ApiResponse<>(true, "Protocol updated", masterDataService.updateProtocol(id, req, isSuperAdmin)));
    }

    @DeleteMapping("/protocols/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteProtocol(@PathVariable UUID id) {
        // PD-40-04: SUPADM-only, non-grantable system-row override — see updateChakra.
        boolean isSuperAdmin = rbacAuthz.hasPermission("MASTER_DATA_PROTOCOLS", "ADMIN");
        masterDataService.deleteProtocol(id, isSuperAdmin);
        return ResponseEntity.ok(new ApiResponse<>(true, "Protocol deleted", null));
    }

    // importProtocols is bulk protocol creation from a CSV, so CREATE is the correct verb.
    @PostMapping(value = "/protocols/import", consumes = "multipart/form-data")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','CREATE')")
    public ResponseEntity<ApiResponse<ImportResult>> importProtocols(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Import complete", protocolImportService.importFile(file)));
    }
}
