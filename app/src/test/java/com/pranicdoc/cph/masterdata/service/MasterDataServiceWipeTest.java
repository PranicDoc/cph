package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.MasterDataType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.MasterDataWipeResult;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
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
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused unit coverage for {@link MasterDataService#wipeMasterData} — the SUPADM-only bulk
 * wipe across the four master-data categories. Request-level proof that the endpoint itself
 * is unreachable by a non-SUPADM caller lives in {@code MasterDataControllerAuthorizationTest};
 * this class covers the service's own per-category dispatch and the unconditional deny.
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

    private ChakraCatalog chakra() {
        ChakraCatalog c = new ChakraCatalog();
        c.setId(UUID.randomUUID());
        return c;
    }

    private PranaColorCatalog color() {
        PranaColorCatalog c = new PranaColorCatalog();
        c.setId(UUID.randomUUID());
        return c;
    }

    private ProtocolColorMapping mapping() {
        ProtocolColorMapping m = new ProtocolColorMapping();
        m.setId(UUID.randomUUID());
        return m;
    }

    private ProtocolCatalog protocol() {
        ProtocolCatalog p = new ProtocolCatalog();
        p.setId(UUID.randomUUID());
        return p;
    }

    @Test
    void nonSuperAdminIsDeniedOutright_regardlessOfType() {
        assertThrows(AccessDeniedException.class, () -> masterDataService.wipeMasterData(MasterDataType.ALL, false));
        assertThrows(AccessDeniedException.class,
                () -> masterDataService.wipeMasterData(MasterDataType.PROTOCOLS, false));

        verify(chakraRepo, never()).findAll();
        verify(colorRepo, never()).findAll();
        verify(mappingRepo, never()).findAll();
        verify(protocolRepo, never()).findAll();
    }

    @Test
    void all_softDeletesEveryCategoryAndReturnsPerCategoryCounts() {
        when(chakraRepo.findAll()).thenReturn(List.of(chakra(), chakra()));
        when(colorRepo.findAll()).thenReturn(List.of(color()));
        when(mappingRepo.findAll()).thenReturn(List.of(mapping(), mapping(), mapping()));
        when(protocolRepo.findAll()).thenReturn(List.of(protocol()));

        MasterDataWipeResult result = masterDataService.wipeMasterData(MasterDataType.ALL, true);

        assertEquals(new MasterDataWipeResult(MasterDataType.ALL, 2, 1, 3, 1), result);
        verify(chakraRepo).saveAll(anyListOfSize(2));
        verify(colorRepo).saveAll(anyListOfSize(1));
        verify(mappingRepo).saveAll(anyListOfSize(3));
        verify(protocolRepo).saveAll(anyListOfSize(1));
    }

    @Test
    void protocols_onlyTouchesTheProtocolCatalog() {
        when(protocolRepo.findAll()).thenReturn(List.of(protocol(), protocol()));

        MasterDataWipeResult result = masterDataService.wipeMasterData(MasterDataType.PROTOCOLS, true);

        assertEquals(new MasterDataWipeResult(MasterDataType.PROTOCOLS, 0, 0, 0, 2), result);
        verify(chakraRepo, never()).findAll();
        verify(colorRepo, never()).findAll();
        verify(mappingRepo, never()).findAll();
    }

    @Test
    void chakras_onlyTouchesTheChakraCatalog() {
        when(chakraRepo.findAll()).thenReturn(List.of(chakra()));

        MasterDataWipeResult result = masterDataService.wipeMasterData(MasterDataType.CHAKRAS, true);

        assertEquals(new MasterDataWipeResult(MasterDataType.CHAKRAS, 1, 0, 0, 0), result);
        verify(colorRepo, never()).findAll();
        verify(mappingRepo, never()).findAll();
        verify(protocolRepo, never()).findAll();
    }

    @Test
    void colors_onlyTouchesThePranaColorCatalog() {
        when(colorRepo.findAll()).thenReturn(List.of(color(), color()));

        MasterDataWipeResult result = masterDataService.wipeMasterData(MasterDataType.COLORS, true);

        assertEquals(new MasterDataWipeResult(MasterDataType.COLORS, 0, 2, 0, 0), result);
        verify(chakraRepo, never()).findAll();
        verify(mappingRepo, never()).findAll();
        verify(protocolRepo, never()).findAll();
    }

    @Test
    void mappings_onlyTouchesProtocolColorMappings() {
        when(mappingRepo.findAll()).thenReturn(List.of(mapping()));

        MasterDataWipeResult result = masterDataService.wipeMasterData(MasterDataType.MAPPINGS, true);

        assertEquals(new MasterDataWipeResult(MasterDataType.MAPPINGS, 0, 0, 1, 0), result);
        verify(chakraRepo, never()).findAll();
        verify(colorRepo, never()).findAll();
        verify(protocolRepo, never()).findAll();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> anyListOfSize(int size) {
        return org.mockito.ArgumentMatchers.argThat(list -> list != null && list.size() == size);
    }
}
