package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.ChakraRequest;
import com.pranicdoc.cph.masterdata.dto.PranaColorRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolRequest;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolColorMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MDIMPORT-19: proves the cross-healer ownership guard added to
 * updateChakra/updateColor/updateProtocol/deleteMapping denies a non-SUPADM
 * caller from mutating another healer's custom row, while preserving the
 * pre-existing SYSTEM-row denial and SUPADM override behavior. Mirrors the
 * cross-healer test shape already used by ChakraImportServiceTest et al.
 * (Phase 2 CR-01).
 */
@ExtendWith(MockitoExtension.class)
class MasterDataServiceTest {

    @Mock private ChakraCatalogRepository chakraRepo;
    @Mock private PranaColorCatalogRepository colorRepo;
    @Mock private ProtocolColorMappingRepository mappingRepo;
    @Mock private ProtocolCatalogRepository protocolRepo;
    @Mock private SecurityContextUtil securityContextUtil;

    private MasterDataService service;

    private static final UUID CALLER_HEALER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MasterDataService(chakraRepo, colorRepo, mappingRepo, protocolRepo, securityContextUtil);
        lenient().when(securityContextUtil.getCurrentHealerId()).thenReturn(CALLER_HEALER_ID);
    }

    private ChakraRequest chakraRequest() {
        return new ChakraRequest("Crown Chakra", "CROWN", "major", "Top of head", null);
    }

    private PranaColorRequest colorRequest() {
        return new PranaColorRequest("Violet", "VIOLET", "#8F00FF", null, null, null, null);
    }

    private ProtocolRequest protocolRequest() {
        return new ProtocolRequest("Basic Meditation", "Book1", 10, "category", "desc", "ch1", ProtocolType.PROTOCOL, null);
    }

    // ── updateChakra ─────────────────────────────────────────────────────────

    @Test
    void updateChakra_nonSuperAdmin_cannotOverwriteAnotherHealersCustomRow() {
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setHealerId(UUID.randomUUID());
        when(chakraRepo.findById(any(UUID.class))).thenReturn(Optional.of(chakra));

        assertThrows(AccessDeniedException.class,
                () -> service.updateChakra(UUID.randomUUID(), chakraRequest(), false));
        verify(chakraRepo, never()).save(any());
    }

    @Test
    void updateChakra_nonSuperAdmin_canEditOwnCustomRow() {
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setHealerId(CALLER_HEALER_ID);
        when(chakraRepo.findById(any(UUID.class))).thenReturn(Optional.of(chakra));
        when(chakraRepo.save(any(ChakraCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.updateChakra(UUID.randomUUID(), chakraRequest(), false));
        verify(chakraRepo).save(any());
    }

    @Test
    void updateChakra_nonSuperAdmin_cannotOverwriteSystemRow() {
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setHealerId(null);
        when(chakraRepo.findById(any(UUID.class))).thenReturn(Optional.of(chakra));

        assertThrows(AccessDeniedException.class,
                () -> service.updateChakra(UUID.randomUUID(), chakraRequest(), false));
        verify(chakraRepo, never()).save(any());
    }

    @Test
    void updateChakra_superAdmin_canOverwriteAnotherHealersCustomRow() {
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setHealerId(UUID.randomUUID());
        when(chakraRepo.findById(any(UUID.class))).thenReturn(Optional.of(chakra));
        when(chakraRepo.save(any(ChakraCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.updateChakra(UUID.randomUUID(), chakraRequest(), true));
        verify(chakraRepo).save(any());
    }

    @Test
    void updateChakra_superAdmin_canEditSystemRow() {
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setHealerId(null);
        when(chakraRepo.findById(any(UUID.class))).thenReturn(Optional.of(chakra));
        when(chakraRepo.save(any(ChakraCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.updateChakra(UUID.randomUUID(), chakraRequest(), true));
        verify(chakraRepo).save(any());
    }

    // ── deleteChakra ─────────────────────────────────────────────────────────

    @Test
    void deleteChakra_nonSuperAdmin_cannotDeleteAnotherHealersCustomRow() {
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setHealerId(UUID.randomUUID());
        when(chakraRepo.findById(any(UUID.class))).thenReturn(Optional.of(chakra));

        assertThrows(AccessDeniedException.class,
                () -> service.deleteChakra(UUID.randomUUID(), false));
        verify(chakraRepo, never()).save(any());
    }

    @Test
    void deleteChakra_nonSuperAdmin_canDeleteOwnCustomRow() {
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setHealerId(CALLER_HEALER_ID);
        when(chakraRepo.findById(any(UUID.class))).thenReturn(Optional.of(chakra));
        when(chakraRepo.save(any(ChakraCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.deleteChakra(UUID.randomUUID(), false));
        verify(chakraRepo).save(any());
    }

    @Test
    void deleteChakra_nonSuperAdmin_cannotDeleteSystemRow() {
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setHealerId(null);
        when(chakraRepo.findById(any(UUID.class))).thenReturn(Optional.of(chakra));

        assertThrows(AccessDeniedException.class,
                () -> service.deleteChakra(UUID.randomUUID(), false));
        verify(chakraRepo, never()).save(any());
    }

    @Test
    void deleteChakra_superAdmin_canDeleteAnotherHealersCustomRow() {
        ChakraCatalog chakra = new ChakraCatalog();
        chakra.setHealerId(UUID.randomUUID());
        when(chakraRepo.findById(any(UUID.class))).thenReturn(Optional.of(chakra));
        when(chakraRepo.save(any(ChakraCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.deleteChakra(UUID.randomUUID(), true));
        verify(chakraRepo).save(any());
    }

    // ── updateColor ──────────────────────────────────────────────────────────

    @Test
    void updateColor_nonSuperAdmin_cannotOverwriteAnotherHealersCustomRow() {
        PranaColorCatalog color = new PranaColorCatalog();
        color.setHealerId(UUID.randomUUID());
        when(colorRepo.findById(any(UUID.class))).thenReturn(Optional.of(color));

        assertThrows(AccessDeniedException.class,
                () -> service.updateColor(UUID.randomUUID(), colorRequest(), false));
        verify(colorRepo, never()).save(any());
    }

    @Test
    void updateColor_nonSuperAdmin_canEditOwnCustomRow() {
        PranaColorCatalog color = new PranaColorCatalog();
        color.setHealerId(CALLER_HEALER_ID);
        when(colorRepo.findById(any(UUID.class))).thenReturn(Optional.of(color));
        when(colorRepo.save(any(PranaColorCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.updateColor(UUID.randomUUID(), colorRequest(), false));
        verify(colorRepo).save(any());
    }

    @Test
    void updateColor_nonSuperAdmin_cannotOverwriteSystemRow() {
        PranaColorCatalog color = new PranaColorCatalog();
        color.setHealerId(null);
        when(colorRepo.findById(any(UUID.class))).thenReturn(Optional.of(color));

        assertThrows(AccessDeniedException.class,
                () -> service.updateColor(UUID.randomUUID(), colorRequest(), false));
        verify(colorRepo, never()).save(any());
    }

    @Test
    void updateColor_superAdmin_canOverwriteAnotherHealersCustomRow() {
        PranaColorCatalog color = new PranaColorCatalog();
        color.setHealerId(UUID.randomUUID());
        when(colorRepo.findById(any(UUID.class))).thenReturn(Optional.of(color));
        when(colorRepo.save(any(PranaColorCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.updateColor(UUID.randomUUID(), colorRequest(), true));
        verify(colorRepo).save(any());
    }

    @Test
    void updateColor_superAdmin_canEditSystemRow() {
        PranaColorCatalog color = new PranaColorCatalog();
        color.setHealerId(null);
        when(colorRepo.findById(any(UUID.class))).thenReturn(Optional.of(color));
        when(colorRepo.save(any(PranaColorCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.updateColor(UUID.randomUUID(), colorRequest(), true));
        verify(colorRepo).save(any());
    }

    // ── deleteColor ──────────────────────────────────────────────────────────

    @Test
    void deleteColor_nonSuperAdmin_cannotDeleteAnotherHealersCustomRow() {
        PranaColorCatalog color = new PranaColorCatalog();
        color.setHealerId(UUID.randomUUID());
        when(colorRepo.findById(any(UUID.class))).thenReturn(Optional.of(color));

        assertThrows(AccessDeniedException.class,
                () -> service.deleteColor(UUID.randomUUID(), false));
        verify(colorRepo, never()).save(any());
    }

    @Test
    void deleteColor_nonSuperAdmin_canDeleteOwnCustomRow() {
        PranaColorCatalog color = new PranaColorCatalog();
        color.setHealerId(CALLER_HEALER_ID);
        when(colorRepo.findById(any(UUID.class))).thenReturn(Optional.of(color));
        when(colorRepo.save(any(PranaColorCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.deleteColor(UUID.randomUUID(), false));
        verify(colorRepo).save(any());
    }

    @Test
    void deleteColor_nonSuperAdmin_cannotDeleteSystemRow() {
        PranaColorCatalog color = new PranaColorCatalog();
        color.setHealerId(null);
        when(colorRepo.findById(any(UUID.class))).thenReturn(Optional.of(color));

        assertThrows(AccessDeniedException.class,
                () -> service.deleteColor(UUID.randomUUID(), false));
        verify(colorRepo, never()).save(any());
    }

    @Test
    void deleteColor_superAdmin_canDeleteAnotherHealersCustomRow() {
        PranaColorCatalog color = new PranaColorCatalog();
        color.setHealerId(UUID.randomUUID());
        when(colorRepo.findById(any(UUID.class))).thenReturn(Optional.of(color));
        when(colorRepo.save(any(PranaColorCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.deleteColor(UUID.randomUUID(), true));
        verify(colorRepo).save(any());
    }

    // ── updateProtocol ───────────────────────────────────────────────────────

    @Test
    void updateProtocol_nonSuperAdmin_cannotOverwriteAnotherHealersCustomRow() {
        ProtocolCatalog protocol = new ProtocolCatalog();
        protocol.setHealerId(UUID.randomUUID());
        when(protocolRepo.findById(any(UUID.class))).thenReturn(Optional.of(protocol));

        assertThrows(AccessDeniedException.class,
                () -> service.updateProtocol(UUID.randomUUID(), protocolRequest(), false));
        verify(protocolRepo, never()).save(any());
    }

    @Test
    void updateProtocol_nonSuperAdmin_canEditOwnCustomRow() {
        ProtocolCatalog protocol = new ProtocolCatalog();
        protocol.setHealerId(CALLER_HEALER_ID);
        when(protocolRepo.findById(any(UUID.class))).thenReturn(Optional.of(protocol));
        when(protocolRepo.save(any(ProtocolCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.updateProtocol(UUID.randomUUID(), protocolRequest(), false));
        verify(protocolRepo).save(any());
    }

    @Test
    void updateProtocol_nonSuperAdmin_cannotOverwriteSystemRow() {
        ProtocolCatalog protocol = new ProtocolCatalog();
        protocol.setHealerId(null);
        when(protocolRepo.findById(any(UUID.class))).thenReturn(Optional.of(protocol));

        assertThrows(AccessDeniedException.class,
                () -> service.updateProtocol(UUID.randomUUID(), protocolRequest(), false));
        verify(protocolRepo, never()).save(any());
    }

    @Test
    void updateProtocol_superAdmin_canOverwriteAnotherHealersCustomRow() {
        ProtocolCatalog protocol = new ProtocolCatalog();
        protocol.setHealerId(UUID.randomUUID());
        when(protocolRepo.findById(any(UUID.class))).thenReturn(Optional.of(protocol));
        when(protocolRepo.save(any(ProtocolCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.updateProtocol(UUID.randomUUID(), protocolRequest(), true));
        verify(protocolRepo).save(any());
    }

    @Test
    void updateProtocol_superAdmin_canEditSystemRow() {
        ProtocolCatalog protocol = new ProtocolCatalog();
        protocol.setHealerId(null);
        when(protocolRepo.findById(any(UUID.class))).thenReturn(Optional.of(protocol));
        when(protocolRepo.save(any(ProtocolCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.updateProtocol(UUID.randomUUID(), protocolRequest(), true));
        verify(protocolRepo).save(any());
    }

    // ── deleteProtocol ───────────────────────────────────────────────────────

    @Test
    void deleteProtocol_nonSuperAdmin_cannotDeleteAnotherHealersCustomRow() {
        ProtocolCatalog protocol = new ProtocolCatalog();
        protocol.setHealerId(UUID.randomUUID());
        when(protocolRepo.findById(any(UUID.class))).thenReturn(Optional.of(protocol));

        assertThrows(AccessDeniedException.class,
                () -> service.deleteProtocol(UUID.randomUUID(), false));
        verify(protocolRepo, never()).save(any());
    }

    @Test
    void deleteProtocol_nonSuperAdmin_canDeleteOwnCustomRow() {
        ProtocolCatalog protocol = new ProtocolCatalog();
        protocol.setHealerId(CALLER_HEALER_ID);
        when(protocolRepo.findById(any(UUID.class))).thenReturn(Optional.of(protocol));
        when(protocolRepo.save(any(ProtocolCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.deleteProtocol(UUID.randomUUID(), false));
        verify(protocolRepo).save(any());
    }

    @Test
    void deleteProtocol_nonSuperAdmin_cannotDeleteSystemRow() {
        ProtocolCatalog protocol = new ProtocolCatalog();
        protocol.setHealerId(null);
        when(protocolRepo.findById(any(UUID.class))).thenReturn(Optional.of(protocol));

        assertThrows(AccessDeniedException.class,
                () -> service.deleteProtocol(UUID.randomUUID(), false));
        verify(protocolRepo, never()).save(any());
    }

    @Test
    void deleteProtocol_superAdmin_canDeleteAnotherHealersCustomRow() {
        ProtocolCatalog protocol = new ProtocolCatalog();
        protocol.setHealerId(UUID.randomUUID());
        when(protocolRepo.findById(any(UUID.class))).thenReturn(Optional.of(protocol));
        when(protocolRepo.save(any(ProtocolCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.deleteProtocol(UUID.randomUUID(), true));
        verify(protocolRepo).save(any());
    }

    // ── deleteMapping ────────────────────────────────────────────────────────

    @Test
    void deleteMapping_nonSuperAdmin_cannotOverwriteAnotherHealersCustomRow() {
        ProtocolColorMapping mapping = new ProtocolColorMapping();
        mapping.setHealerId(UUID.randomUUID());
        when(mappingRepo.findById(any(UUID.class))).thenReturn(Optional.of(mapping));

        assertThrows(AccessDeniedException.class,
                () -> service.deleteMapping(UUID.randomUUID(), false));
        verify(mappingRepo, never()).save(any());
    }

    @Test
    void deleteMapping_nonSuperAdmin_canDeleteOwnCustomRow() {
        ProtocolColorMapping mapping = new ProtocolColorMapping();
        mapping.setHealerId(CALLER_HEALER_ID);
        when(mappingRepo.findById(any(UUID.class))).thenReturn(Optional.of(mapping));
        when(mappingRepo.save(any(ProtocolColorMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.deleteMapping(UUID.randomUUID(), false));
        verify(mappingRepo).save(any());
    }

    @Test
    void deleteMapping_nonSuperAdmin_cannotOverwriteSystemRow() {
        ProtocolColorMapping mapping = new ProtocolColorMapping();
        mapping.setHealerId(null);
        when(mappingRepo.findById(any(UUID.class))).thenReturn(Optional.of(mapping));

        assertThrows(AccessDeniedException.class,
                () -> service.deleteMapping(UUID.randomUUID(), false));
        verify(mappingRepo, never()).save(any());
    }

    @Test
    void deleteMapping_superAdmin_canOverwriteAnotherHealersCustomRow() {
        ProtocolColorMapping mapping = new ProtocolColorMapping();
        mapping.setHealerId(UUID.randomUUID());
        when(mappingRepo.findById(any(UUID.class))).thenReturn(Optional.of(mapping));
        when(mappingRepo.save(any(ProtocolColorMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.deleteMapping(UUID.randomUUID(), true));
        verify(mappingRepo).save(any());
    }
}
