package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraColorRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolColorMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@link MasterDataService#searchProtocols}: the paged, searchable protocol list. */
@ExtendWith(MockitoExtension.class)
class MasterDataServiceProtocolSearchTest {

    @Mock private ChakraCatalogRepository chakraRepo;
    @Mock private PranaColorCatalogRepository colorRepo;
    @Mock private ProtocolColorMappingRepository mappingRepo;
    @Mock private ProtocolCatalogRepository protocolRepo;
    @Mock private ProtocolChakraRepository protocolChakraRepo;
    @Mock private ProtocolChakraColorRepository protocolChakraColorRepo;
    @Mock private SecurityContextUtil securityContextUtil;

    @InjectMocks private MasterDataService masterDataService;

    @Test
    void searchesCaseInsensitivelyWithATrimmedContainsPattern() {
        UUID healerId = UUID.randomUUID();
        when(protocolRepo.search(eq(healerId), eq(ProtocolType.PROTOCOL), eq("%blue prana%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new ProtocolCatalog())));

        PageResponse<ProtocolCatalog> page =
                masterDataService.searchProtocols(healerId, ProtocolType.PROTOCOL, "  Blue Prana ", 0, 50);

        assertEquals(1, page.content().size());
    }

    @Test
    void blankSearchMatchesEverythingAndPagesByBookPageThenName() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(protocolRepo.search(isNull(), isNull(), eq(""), pageable.capture())).thenReturn(new PageImpl<>(List.of()));

        masterDataService.searchProtocols(null, null, null, 2, 50);

        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(50, pageable.getValue().getPageSize());
        assertEquals(Sort.by("sourceBook", "pageNumber", "name"), pageable.getValue().getSort());
    }

    @Test
    void pageSizeIsCappedAndNegativePageClamped() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(protocolRepo.search(isNull(), isNull(), eq(""), pageable.capture())).thenReturn(new PageImpl<>(List.of()));

        masterDataService.searchProtocols(null, null, "", -3, 5000);

        verify(protocolRepo).search(isNull(), isNull(), eq(""), any(Pageable.class));
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
    }
}
