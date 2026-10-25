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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the CLEANSING / ENERGISING category on prana colors: one category per row,
 * and a name may exist once per category (a color used both ways is two rows).
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

    private static PranaColorRequest request(String name, String code, ColorUsageType category) {
        return new PranaColorRequest(name, code, "#E0FFE0", List.of("Mild cleansing"), category, null);
    }

    @Test
    void createColor_storesTheCategory_whenThatNameIsFreeInThatCategory() {
        when(colorRepo.existsByNameIgnoreCaseAndCategory("Light Whitish Green", ColorUsageType.CLEANSING))
                .thenReturn(false);
        when(colorRepo.save(any(PranaColorCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        PranaColorCatalog saved = masterDataService.createColor(
                request("Light Whitish Green", "lwg_c", ColorUsageType.CLEANSING));

        assertEquals(ColorUsageType.CLEANSING, saved.getCategory());
        assertEquals("LWG_C", saved.getCode());
    }

    @Test
    void createColor_rejectsADuplicateNameInTheSameCategory() {
        when(colorRepo.existsByNameIgnoreCaseAndCategory("Light Whitish Green", ColorUsageType.ENERGISING))
                .thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                masterDataService.createColor(request("Light Whitish Green", "LWG_E", ColorUsageType.ENERGISING)));

        assertEquals("\"Light Whitish Green\" already exists in energising colors", ex.getMessage());
        verify(colorRepo, never()).save(any());
    }

    @Test
    void updateColor_movesTheColorToAnotherCategory_unlessThatNameIsTakenThere() {
        UUID id = UUID.randomUUID();
        PranaColorCatalog existing = PranaColorCatalog.builder()
                .code("ORANGE").name("Orange Prana").colorHex("#FFA500").healerId(UUID.randomUUID())
                .category(ColorUsageType.ENERGISING)
                .build();
        when(colorRepo.findById(id)).thenReturn(Optional.of(existing));
        when(colorRepo.existsByNameIgnoreCaseAndCategoryAndIdNot("Orange Prana", ColorUsageType.CLEANSING, id))
                .thenReturn(false);
        when(colorRepo.save(any(PranaColorCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        PranaColorCatalog saved = masterDataService.updateColor(id,
                request("Orange Prana", "ORANGE", ColorUsageType.CLEANSING), false);

        assertEquals(ColorUsageType.CLEANSING, saved.getCategory());

        when(colorRepo.existsByNameIgnoreCaseAndCategoryAndIdNot("Orange Prana", ColorUsageType.ENERGISING, id))
                .thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> masterDataService.updateColor(id,
                request("Orange Prana", "ORANGE", ColorUsageType.ENERGISING), false));
    }
}
