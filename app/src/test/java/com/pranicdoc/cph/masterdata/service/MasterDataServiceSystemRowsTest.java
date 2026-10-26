package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.ChakraRequest;
import com.pranicdoc.cph.masterdata.dto.PranaColorRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolRequest;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraColorRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolColorMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Rows a super admin creates are system rows (no owning healer); a healer's stay their own. */
@ExtendWith(MockitoExtension.class)
class MasterDataServiceSystemRowsTest {

    @Mock private ChakraCatalogRepository chakraRepo;
    @Mock private PranaColorCatalogRepository colorRepo;
    @Mock private ProtocolColorMappingRepository mappingRepo;
    @Mock private ProtocolCatalogRepository protocolRepo;
    @Mock private ProtocolChakraRepository protocolChakraRepo;
    @Mock private ProtocolChakraColorRepository protocolChakraColorRepo;
    @Mock private SecurityContextUtil securityContextUtil;

    @InjectMocks private MasterDataService masterDataService;

    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(securityContextUtil.getCurrentHealerId()).thenReturn(callerId);
        lenient().when(chakraRepo.save(any(ChakraCatalog.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(colorRepo.save(any(PranaColorCatalog.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(protocolRepo.save(any(ProtocolCatalog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void actAs(String role) {
        when(securityContextUtil.getCurrentRole()).thenReturn(role);
    }

    private static ChakraRequest chakra() {
        return new ChakraRequest("Crown", "crown", null, null, null);
    }

    private static PranaColorRequest color() {
        return new PranaColorRequest("Violet", "violet", "#8000FF", List.of(), ColorUsageType.ENERGISING, null);
    }

    private static ProtocolRequest protocol() {
        return new ProtocolRequest("Sweeping", "Book", 1, null, null, null, ProtocolType.PROTOCOL, null);
    }

    @Test
    void superAdminCreates_systemRows() {
        actAs("SUPADM");

        assertNull(masterDataService.createChakra(chakra()).getHealerId());
        assertNull(masterDataService.createColor(color()).getHealerId());
        assertNull(masterDataService.createProtocol(protocol()).getHealerId());
    }

    @Test
    void healerCreates_theirOwnRows() {
        actAs("HEALER");

        assertEquals(callerId, masterDataService.createChakra(chakra()).getHealerId());
        assertEquals(callerId, masterDataService.createColor(color()).getHealerId());
        assertEquals(callerId, masterDataService.createProtocol(protocol()).getHealerId());
    }
}
