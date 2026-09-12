package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import org.springframework.web.multipart.MultipartFile;

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

    ImportResult importFile(MultipartFile file);
}
