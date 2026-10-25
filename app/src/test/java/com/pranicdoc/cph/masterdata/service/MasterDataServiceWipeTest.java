package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.MasterDataType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.MasterDataWipeResult;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraColorRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolColorMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused unit coverage for {@link MasterDataService#wipeMasterData} — the SUPADM-only bulk
 * wipe across the four master-data categories. Request-level proof that the endpoint itself
 * is unreachable by a non-SUPADM caller lives in {@code MasterDataControllerAuthorizationTest};
 * this class covers the service's own per-category dispatch, FK-safe delete order and the unconditional deny.
 */
@ExtendWith(MockitoExtension.class)
class MasterDataServiceWipeTest {

    @Mock private ChakraCatalogRepository chakraRepo;
    @Mock private PranaColorCatalogRepository colorRepo;
    @Mock private ProtocolColorMappingRepository mappingRepo;
    @Mock private ProtocolCatalogRepository protocolRepo;
    @Mock private ProtocolChakraRepository protocolChakraRepo;
    @Mock private ProtocolChakraColorRepository protocolChakraColorRepo;
    @Mock private SecurityContextUtil securityContextUtil;

    @InjectMocks private MasterDataService masterDataService;

    @Test
    void nonSuperAdminIsDeniedOutright_regardlessOfType() {
        assertThrows(AccessDeniedException.class, () -> masterDataService.wipeMasterData(MasterDataType.ALL, false));
        assertThrows(AccessDeniedException.class,
                () -> masterDataService.wipeMasterData(MasterDataType.PROTOCOLS, false));

        verifyNoInteractions(chakraRepo, colorRepo, mappingRepo, protocolRepo, protocolChakraRepo,
                protocolChakraColorRepo);
    }

    @Test
    void all_hardDeletesEveryCategoryDependentsFirstAndReturnsPerCategoryCounts() {
        when(mappingRepo.hardDeleteAll()).thenReturn(3);
        when(colorRepo.hardDeleteAll()).thenReturn(1);
        when(chakraRepo.hardDeleteAll()).thenReturn(2);
        when(protocolRepo.hardDeleteAll()).thenReturn(5);

        MasterDataWipeResult result = masterDataService.wipeMasterData(MasterDataType.ALL, true);

        assertEquals(new MasterDataWipeResult(MasterDataType.ALL, 2, 1, 3, 5), result);
        // FK order: link colors -> protocol/chakra links -> mappings & color properties -> catalogs.
        InOrder order = inOrder(protocolChakraColorRepo, protocolChakraRepo, mappingRepo, colorRepo,
                chakraRepo, protocolRepo);
        order.verify(protocolChakraColorRepo).hardDeleteAll();
        order.verify(protocolChakraRepo).hardDeleteAll();
        order.verify(mappingRepo).hardDeleteAll();
        order.verify(colorRepo).hardDeleteAllProperties();
        order.verify(colorRepo).hardDeleteAll();
        order.verify(chakraRepo).hardDeleteAll();
        order.verify(protocolRepo).hardDeleteAll();
        // Hard delete only: nothing is loaded and soft-deleted through the persistence context.
        verify(chakraRepo, never()).findAll();
        verify(chakraRepo, never()).saveAll(any());
    }

    @Test
    void protocols_removesLinksBeforeTheProtocolCatalogAndLeavesTheOtherCatalogsAlone() {
        when(protocolRepo.hardDeleteAll()).thenReturn(2);

        MasterDataWipeResult result = masterDataService.wipeMasterData(MasterDataType.PROTOCOLS, true);

        assertEquals(new MasterDataWipeResult(MasterDataType.PROTOCOLS, 0, 0, 0, 2), result);
        InOrder order = inOrder(protocolChakraColorRepo, protocolChakraRepo, protocolRepo);
        order.verify(protocolChakraColorRepo).hardDeleteAll();
        order.verify(protocolChakraRepo).hardDeleteAll();
        order.verify(protocolRepo).hardDeleteAll();
        verifyNoInteractions(chakraRepo, colorRepo, mappingRepo);
    }

    @Test
    void chakras_removesLinksBeforeTheChakraCatalogAndLeavesTheOtherCatalogsAlone() {
        when(chakraRepo.hardDeleteAll()).thenReturn(1);

        MasterDataWipeResult result = masterDataService.wipeMasterData(MasterDataType.CHAKRAS, true);

        assertEquals(new MasterDataWipeResult(MasterDataType.CHAKRAS, 1, 0, 0, 0), result);
        InOrder order = inOrder(protocolChakraColorRepo, protocolChakraRepo, chakraRepo);
        order.verify(protocolChakraColorRepo).hardDeleteAll();
        order.verify(protocolChakraRepo).hardDeleteAll();
        order.verify(chakraRepo).hardDeleteAll();
        verifyNoInteractions(colorRepo, mappingRepo, protocolRepo);
    }

    @Test
    void colors_removesColorLinksMappingsAndPropertiesBeforeTheColorCatalog() {
        when(mappingRepo.hardDeleteAll()).thenReturn(4);
        when(colorRepo.hardDeleteAll()).thenReturn(2);

        MasterDataWipeResult result = masterDataService.wipeMasterData(MasterDataType.COLORS, true);

        // mappings has an FK to prana_color_catalog, so it is removed (and counted) with the colors.
        assertEquals(new MasterDataWipeResult(MasterDataType.COLORS, 0, 2, 4, 0), result);
        InOrder order = inOrder(protocolChakraColorRepo, mappingRepo, colorRepo);
        order.verify(protocolChakraColorRepo).hardDeleteAll();
        order.verify(mappingRepo).hardDeleteAll();
        order.verify(colorRepo).hardDeleteAllProperties();
        order.verify(colorRepo).hardDeleteAll();
        verify(protocolChakraRepo, never()).hardDeleteAll();
        verifyNoInteractions(chakraRepo, protocolRepo);
    }

    @Test
    void mappings_onlyTouchesProtocolColorMappings() {
        when(mappingRepo.hardDeleteAll()).thenReturn(1);

        MasterDataWipeResult result = masterDataService.wipeMasterData(MasterDataType.MAPPINGS, true);

        assertEquals(new MasterDataWipeResult(MasterDataType.MAPPINGS, 0, 0, 1, 0), result);
        verifyNoInteractions(chakraRepo, colorRepo, protocolRepo, protocolChakraRepo, protocolChakraColorRepo);
    }
}
