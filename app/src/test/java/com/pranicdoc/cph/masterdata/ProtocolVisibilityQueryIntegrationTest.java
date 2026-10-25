package com.pranicdoc.cph.masterdata;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolChakra;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB coverage for the "system + the healer's own" reads scoped to one protocol or one
 * protocol type. Both were derived queries ("...AndHealerIdIsNullOrHealerId") that Spring Data
 * reads as (scope AND system) OR own, so a healer's own rows leaked in from other protocols /
 * other types. Same {@code @DataJpaTest} slice as {@link ProtocolChakraColorCrudIntegrationTest}.
 */
@DataJpaTest
@ActiveProfiles("test")
class ProtocolVisibilityQueryIntegrationTest {

    @Autowired private ProtocolCatalogRepository protocolRepo;
    @Autowired private ChakraCatalogRepository chakraRepo;
    @Autowired private ProtocolChakraRepository protocolChakraRepo;

    @MockBean private HealerCacheService healerCacheService;
    @MockBean private SecurityContextUtil securityContextUtil;

    private final UUID healerId = UUID.randomUUID();
    private final UUID otherHealerId = UUID.randomUUID();
    private ChakraCatalog crown;
    private ChakraCatalog spleen;

    @BeforeEach
    void setUp() {
        crown = chakra("CROWN_VIS_T", "Crown Chakra");
        spleen = chakra("SPLEEN_VIS_T", "Spleen Chakra");
    }

    @Test
    void findVisibleByProtocolId_returnsOnlyThatProtocolsSystemAndOwnLinks() {
        ProtocolCatalog fever = protocol("Fever", ProtocolType.PROTOCOL, null);
        ProtocolCatalog cough = protocol("Cough", ProtocolType.PROTOCOL, null);
        ProtocolChakra system = link(fever, crown, null);
        ProtocolChakra own = link(fever, spleen, healerId);
        link(fever, crown, otherHealerId);   // another healer's addition on the same protocol
        link(cough, spleen, healerId);       // the healer's own link on a different protocol

        assertThat(protocolChakraRepo.findVisibleByProtocolId(fever.getId(), healerId))
                .extracting(ProtocolChakra::getId)
                .containsExactlyInAnyOrder(system.getId(), own.getId());
    }

    @Test
    void findVisibleByProtocolType_returnsOnlyThatTypesSystemAndOwnProtocols() {
        ProtocolCatalog systemSupplementary = protocol("Sleep", ProtocolType.SUPPLEMENTARY, null);
        ProtocolCatalog ownSupplementary = protocol("My Sleep", ProtocolType.SUPPLEMENTARY, healerId);
        protocol("My Fever", ProtocolType.PROTOCOL, healerId);              // own, other type
        protocol("Their Sleep", ProtocolType.SUPPLEMENTARY, otherHealerId); // other healer

        assertThat(protocolRepo.findVisibleByProtocolType(ProtocolType.SUPPLEMENTARY, healerId))
                .extracting(ProtocolCatalog::getId)
                .containsExactlyInAnyOrder(systemSupplementary.getId(), ownSupplementary.getId());
    }

    private ChakraCatalog chakra(String code, String name) {
        ChakraCatalog c = new ChakraCatalog();
        c.setCode(code);
        c.setName(name);
        return chakraRepo.save(c);
    }

    private ProtocolCatalog protocol(String name, ProtocolType type, UUID owner) {
        ProtocolCatalog p = new ProtocolCatalog();
        p.setName(name);
        p.setSourceBook("Test Book");
        p.setPageNumber(1);
        p.setProtocolType(type);
        p.setHealerId(owner);
        return protocolRepo.save(p);
    }

    private ProtocolChakra link(ProtocolCatalog protocol, ChakraCatalog chakra, UUID owner) {
        ProtocolChakra l = new ProtocolChakra();
        l.setProtocol(protocol);
        l.setChakra(chakra);
        l.setHealerId(owner);
        return protocolChakraRepo.save(l);
    }
}
