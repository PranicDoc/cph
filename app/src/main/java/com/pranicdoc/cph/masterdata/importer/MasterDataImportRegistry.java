package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.common.enums.MasterdataType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Type -> importer dispatch map. Spring collection-injects every {@link
 * MasterDataImporter} bean in the context; construction fails fast with
 * {@link IllegalStateException} if two beans claim the same {@link
 * MasterdataType#kind()} — never silently overwrites the dispatch map,
 * which could otherwise route an import to the wrong importer (T-02-01).
 */
@Component
public class MasterDataImportRegistry {

    private final Map<MasterdataType, MasterDataImporter> importers;

    public MasterDataImportRegistry(List<MasterDataImporter> importerBeans) {
        Map<MasterdataType, MasterDataImporter> map = new EnumMap<>(MasterdataType.class);
        for (MasterDataImporter importer : importerBeans) {
            MasterDataImporter existing = map.putIfAbsent(importer.kind(), importer);
            if (existing != null) {
                throw new IllegalStateException("Duplicate MasterDataImporter for kind " + importer.kind());
            }
        }
        this.importers = map;
    }

    public MasterDataImporter get(MasterdataType kind) {
        MasterDataImporter importer = importers.get(kind);
        if (importer == null) {
            throw new IllegalStateException("No MasterDataImporter registered for kind " + kind);
        }
        return importer;
    }
}
