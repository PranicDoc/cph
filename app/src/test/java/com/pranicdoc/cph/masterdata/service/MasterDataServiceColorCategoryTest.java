package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.PranaColorRequest;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the CLEANSING / ENERGISING category on prana colors: create and update
 * store exactly the requested categories, and a color may carry both.
 */
@ExtendWith(MockitoExtension.class)
class MasterDataServiceColorCategoryTest {

    @Mock private ChakraCatalogRepository chakraRepo;
    @Mock private PranaColorCatalogRepository colorRepo;
    @Mock private ProtocolColorMappingRepository mappingRepo;
    @Mock private ProtocolCatalogRepository protocolRepo;
    @Mock private ProtocolChakraRepository protocolChakraRepo;
    @Mock private ProtocolChakraColorRepository protocolChakraColorRepo;
    @Mock private SecurityContextUtil securityContextUtil;

    @InjectMocks private MasterDataService masterDataService;

    @Test
    void createColor_storesRequestedCategories() {
        when(colorRepo.save(any(PranaColorCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        PranaColorCatalog saved = masterDataService.createColor(new PranaColorRequest(
                "Light Whitish Green", "lwg", "#E0FFE0", List.of("Mild cleansing"),
                Set.of(ColorUsageType.CLEANSING, ColorUsageType.ENERGISING), null));

        assertEquals(Set.of(ColorUsageType.CLEANSING, ColorUsageType.ENERGISING), saved.getCategories());
        assertEquals("LWG", saved.getCode());
    }

    @Test
    void updateColor_replacesCategories() {
        UUID id = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        PranaColorCatalog existing = PranaColorCatalog.builder()
                .code("ORANGE").name("Orange Prana").colorHex("#FFA500").healerId(healerId)
                .categories(new HashSet<>(Set.of(ColorUsageType.ENERGISING)))
                .build();
        when(colorRepo.findById(id)).thenReturn(Optional.of(existing));
        when(colorRepo.save(any(PranaColorCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        PranaColorCatalog saved = masterDataService.updateColor(id, new PranaColorRequest(
                "Orange Prana", "ORANGE", "#FFA500", List.of("Expelling"),
                Set.of(ColorUsageType.CLEANSING), null), false);

        assertEquals(Set.of(ColorUsageType.CLEANSING), saved.getCategories());
    }
}
