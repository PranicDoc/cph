package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorView;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraColorRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolColorMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Focused unit coverage for {@link MasterDataService}'s protocol -> chakra ->
 * color read flow ({@link MasterDataService#getChakrasForProtocols} /
 * {@link MasterDataService#getColorsForProtocolsAndChakras}) — the guard
 * clauses and repository delegation, not the query results themselves
 * (covered against a real DB by {@code ProtocolChakraColorRelationshipIntegrationTest}).
 */
@ExtendWith(MockitoExtension.class)
class MasterDataServiceProtocolChakraColorTest {

    @Mock private ChakraCatalogRepository chakraRepo;
    @Mock private PranaColorCatalogRepository colorRepo;
    @Mock private ProtocolColorMappingRepository mappingRepo;
    @Mock private ProtocolCatalogRepository protocolRepo;
    @Mock private ProtocolChakraRepository protocolChakraRepo;
    @Mock private ProtocolChakraColorRepository protocolChakraColorRepo;
    @Mock private SecurityContextUtil securityContextUtil;

    @InjectMocks private MasterDataService masterDataService;

    @Test
    void getChakrasForProtocols_delegatesToRepository() {
        UUID protocolId = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setCode("CROWN");
        chakra.setName("Crown Chakra");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(protocolChakraRepo.findDistinctChakrasByProtocolIdsForHealer(List.of(protocolId), healerId))
                .thenReturn(List.of(chakra));

        List<ChakraCatalog> result = masterDataService.getChakrasForProtocols(List.of(protocolId));

        assertEquals(1, result.size());
        assertEquals("CROWN", result.get(0).getCode());
    }

    @Test
    void getChakrasForProtocols_nullInput_returnsEmptyWithoutQuerying() {
        assertTrue(masterDataService.getChakrasForProtocols(null).isEmpty());
        verifyNoInteractions(protocolChakraRepo);
    }

    @Test
    void getChakrasForProtocols_emptyInput_returnsEmptyWithoutQuerying() {
        assertTrue(masterDataService.getChakrasForProtocols(List.of()).isEmpty());
        verifyNoInteractions(protocolChakraRepo);
    }

    @Test
    void getColorsForProtocolsAndChakras_delegatesToRepository() {
        UUID protocolId = UUID.randomUUID();
        UUID chakraId = UUID.randomUUID();
        UUID colorId = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        ProtocolChakraColorView view = new ProtocolChakraColorView(protocolId, chakraId, colorId, ColorUsageType.CLEANSING);
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(protocolChakraColorRepo.findColorsForProtocolsAndChakrasForHealer(List.of(protocolId), List.of(chakraId), healerId))
                .thenReturn(List.of(view));

        List<ProtocolChakraColorView> result =
                masterDataService.getColorsForProtocolsAndChakras(List.of(protocolId), List.of(chakraId));

        assertEquals(1, result.size());
        assertEquals(colorId, result.get(0).colorId());
        assertEquals(ColorUsageType.CLEANSING, result.get(0).usageType());
    }

    @Test
    void getColorsForProtocolsAndChakras_missingChakraIds_returnsEmptyWithoutQuerying() {
        assertTrue(masterDataService.getColorsForProtocolsAndChakras(List.of(UUID.randomUUID()), List.of()).isEmpty());
        verifyNoInteractions(protocolChakraColorRepo);
    }

    @Test
    void getColorsForProtocolsAndChakras_missingProtocolIds_returnsEmptyWithoutQuerying() {
        assertTrue(masterDataService.getColorsForProtocolsAndChakras(List.of(), List.of(UUID.randomUUID())).isEmpty());
        verify(protocolChakraColorRepo, never()).findColorsForProtocolsAndChakrasForHealer(any(), any(), any());
    }
}
