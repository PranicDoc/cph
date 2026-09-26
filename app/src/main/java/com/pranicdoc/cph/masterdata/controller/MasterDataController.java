package com.pranicdoc.cph.masterdata.controller;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.enums.MasterDataType;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.dto.ChakraRequest;
import com.pranicdoc.cph.masterdata.dto.MasterDataWipeResult;
import com.pranicdoc.cph.masterdata.dto.PranaColorRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorAddRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorGroup;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorUpdateRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorView;
import com.pranicdoc.cph.masterdata.dto.ProtocolColorMappingRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolImportJobView;
import com.pranicdoc.cph.masterdata.dto.ProtocolRequest;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import com.pranicdoc.cph.masterdata.service.MasterDataService;
import com.pranicdoc.cph.masterdata.service.ProtocolImportJobService;
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
    private final ProtocolImportJobService protocolImportJobService;
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

    // ── Protocol -> Chakra -> Color filtered read flow ─────────────────────
    // Step 2: given selected protocol ids, return the distinct chakras they
    // reference. Step 3: given selected protocol ids + chakra ids, return the
    // (colorId, usageType) pairs for that combination. Both gated on the same
    // MASTER_DATA_PROTOCOLS:READ verb as the rest of the protocol surface —
    // this is protocol-relationship data, not a new resource.

    @GetMapping("/protocols/chakras")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','READ')")
    public ResponseEntity<ApiResponse<List<ChakraCatalog>>> getChakrasForProtocols(
            @RequestParam List<UUID> protocolIds) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", masterDataService.getChakrasForProtocols(protocolIds)));
    }

    @GetMapping("/protocols/colors")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','READ')")
    public ResponseEntity<ApiResponse<List<ProtocolChakraColorView>>> getColorsForProtocolsAndChakras(
            @RequestParam List<UUID> protocolIds, @RequestParam List<UUID> chakraIds) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success",
                masterDataService.getColorsForProtocolsAndChakras(protocolIds, chakraIds)));
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

    // Single bulk-wipe endpoint across all four master-data categories, gated on
    // MASTER_DATA_PROTOCOLS:DELETE to admit the request, same as the rest of this
    // cross-cutting protocol-mappings-style surface — but the operation itself is
    // unconditionally SUPADM-only (not row-conditional like the PD-40-04 overrides
    // above): MasterDataService.wipeMasterData throws AccessDeniedException outright
    // for any caller where this in-body flag isn't true, regardless of what DELETE
    // grants they hold. MASTER_DATA_PROTOCOLS does not allow-list the ADMIN verb
    // (Resource.java), so this can only ever be true for SUPADM's RbacAuthz
    // short-circuit — see updateChakra's comment for why that pair is permanently
    // non-grantable.
    @DeleteMapping("/wipe")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','DELETE')")
    public ResponseEntity<ApiResponse<MasterDataWipeResult>> wipeMasterData(
            @RequestParam(defaultValue = "ALL") MasterDataType type) {
        boolean isSuperAdmin = rbacAuthz.hasPermission("MASTER_DATA_PROTOCOLS", "ADMIN");
        return ResponseEntity.ok(new ApiResponse<>(true, "Master data wiped", masterDataService.wipeMasterData(type, isSuperAdmin)));
    }

    // Async: submitting an import returns immediately (QUEUED) — the actual parse+upsert runs
    // in the background (ProtocolImportAsyncRunner) and is tracked via the /import-jobs
    // endpoints below, not returned in this response. importProtocols is bulk protocol
    // creation from a CSV, so CREATE is the correct verb.
    @PostMapping(value = "/protocols/import", consumes = "multipart/form-data")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','CREATE')")
    public ResponseEntity<ApiResponse<ProtocolImportJobView>> importProtocols(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted()
                .body(new ApiResponse<>(true, "Import queued", protocolImportJobService.submitImport(file)));
    }

    @GetMapping("/protocols/import-jobs")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','READ')")
    public ResponseEntity<ApiResponse<List<ProtocolImportJobView>>> getImportJobs() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", protocolImportJobService.getImportJobs()));
    }

    @GetMapping("/protocols/import-jobs/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','READ')")
    public ResponseEntity<ApiResponse<ProtocolImportJobView>> getImportJob(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", protocolImportJobService.getImportJob(id)));
    }

    // ── Single-protocol chakra/color management ─────────────────────────────
    // Distinct from the bulk /protocols/import xlsx path: lets a healer pick
    // ONE protocol and add/edit/remove its chakras and colors directly,
    // without a file. Additions are always the caller's own (healerId =
    // current healer) and visible/editable only by them (or SUPADM) — same
    // gate (MASTER_DATA_PROTOCOLS) as the rest of the protocol surface.

    @GetMapping("/protocols/{protocolId}/chakra-colors")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','READ')")
    public ResponseEntity<ApiResponse<List<ProtocolChakraColorGroup>>> getChakraColorGroupsForProtocol(
            @PathVariable UUID protocolId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Success",
                masterDataService.getChakraColorGroupsForProtocol(protocolId)));
    }

    @PostMapping("/protocols/{protocolId}/chakra-colors")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','CREATE')")
    public ResponseEntity<ApiResponse<List<ProtocolChakraColorGroup>>> addProtocolChakraColors(
            @PathVariable UUID protocolId, @Valid @RequestBody ProtocolChakraColorAddRequest req) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Chakras/colors added",
                masterDataService.addProtocolChakraColors(protocolId, req)));
    }

    @PutMapping("/protocols/{protocolId}/chakra-colors/{protocolChakraId}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','UPDATE')")
    public ResponseEntity<ApiResponse<List<ProtocolChakraColorGroup>>> updateProtocolChakraColor(
            @PathVariable UUID protocolId, @PathVariable UUID protocolChakraId,
            @Valid @RequestBody ProtocolChakraColorUpdateRequest req) {
        // PD-40-04: SUPADM-only, non-grantable system-row override — see updateChakra.
        boolean isSuperAdmin = rbacAuthz.hasPermission("MASTER_DATA_PROTOCOLS", "ADMIN");
        return ResponseEntity.ok(new ApiResponse<>(true, "Chakra colors updated",
                masterDataService.updateProtocolChakraColor(protocolId, protocolChakraId, req, isSuperAdmin)));
    }

    @DeleteMapping("/protocols/{protocolId}/chakra-colors/{protocolChakraId}")
    @PreAuthorize("@rbacAuthz.hasPermission('MASTER_DATA_PROTOCOLS','DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteProtocolChakraColor(
            @PathVariable UUID protocolId, @PathVariable UUID protocolChakraId) {
        // PD-40-04: SUPADM-only, non-grantable system-row override — see updateChakra.
        boolean isSuperAdmin = rbacAuthz.hasPermission("MASTER_DATA_PROTOCOLS", "ADMIN");
        masterDataService.deleteProtocolChakraColor(protocolId, protocolChakraId, isSuperAdmin);
        return ResponseEntity.ok(new ApiResponse<>(true, "Chakra removed from protocol", null));
    }
}
