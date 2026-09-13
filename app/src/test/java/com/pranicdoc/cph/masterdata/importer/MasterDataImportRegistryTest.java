package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No real importer exists yet (they land in wave 2) — uses fake
 * MasterDataImporter test doubles to exercise the registry's dispatch,
 * fail-fast-on-duplicate, and missing-kind behaviors in isolation.
 */
class MasterDataImportRegistryTest {

    private static class FakeImporter implements MasterDataImporter {
        private final MasterdataType kind;

        FakeImporter(MasterdataType kind) {
            this.kind = kind;
        }

        @Override
        public MasterdataType kind() {
            return kind;
        }

        @Override
        public ImportResult importFile(MultipartFile file, boolean isSuperAdmin, UUID healerId) {
            return new ImportResult(0, 0, 0, 0, List.of());
        }
    }

    @Test
    void get_returnsMatchingFakeForEachRegisteredKind() {
        FakeImporter protocolImporter = new FakeImporter(MasterdataType.PROTOCOL);
        FakeImporter chakraImporter = new FakeImporter(MasterdataType.CHAKRA);

        MasterDataImportRegistry registry =
                new MasterDataImportRegistry(List.of(protocolImporter, chakraImporter));

        assertSame(protocolImporter, registry.get(MasterdataType.PROTOCOL));
        assertSame(chakraImporter, registry.get(MasterdataType.CHAKRA));
    }

    @Test
    void constructor_duplicateKind_throwsIllegalStateExceptionImmediately() {
        FakeImporter first = new FakeImporter(MasterdataType.PROTOCOL);
        FakeImporter second = new FakeImporter(MasterdataType.PROTOCOL);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new MasterDataImportRegistry(List.of(first, second)));

        assertTrue(ex.getMessage().contains("PROTOCOL"));
    }

    @Test
    void get_unregisteredKind_throwsIllegalStateExceptionNamingMissingKind() {
        MasterDataImportRegistry registry =
                new MasterDataImportRegistry(List.of(new FakeImporter(MasterdataType.PROTOCOL)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.get(MasterdataType.CHAKRA));

        assertTrue(ex.getMessage().contains("CHAKRA"));
    }
}
