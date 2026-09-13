package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Shared contract implemented by every per-type masterdata importer
 * (Protocol, PranaColor, Chakra, ProtocolColorMapping — D-08). {@link
 * MasterDataImportRegistry} collection-injects every Spring bean
 * implementing this interface and dispatches on {@link #kind()}.
 *
 * <p>Uses {@link MasterdataType} directly as the dispatch key — no separate
 * {@code ImportKind} enum, since {@code MasterdataType} already fills that
 * role (Phase 1).
 */
public interface MasterDataImporter {

    MasterdataType kind();

    /**
     * @param isSuperAdmin resolved by the caller via {@code
     *                      rbacAuthz.hasPermission(resource, "ADMIN")} — mirrors the
     *                      SUPADM-only, non-grantable system-row override used by every
     *                      single-row CRUD mutation in {@code MasterDataService} (PD-40-04).
     * @param healerId      the caller's own healerId ({@code
     *                      SecurityContextUtil#getCurrentHealerId()}), used both to stamp
     *                      newly-created non-SYSTEM rows and to check ownership of an
     *                      existing row before it is overwritten.
     */
    ImportResult importFile(MultipartFile file, boolean isSuperAdmin, UUID healerId);
}
