package com.pranicdoc.cph.masterdata;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorAddRequest;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorEntry;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorGroup;
import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorUpdateRequest;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolChakra;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraColorRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolColorMappingRepository;
import com.pranicdoc.cph.masterdata.service.MasterDataService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * End-to-end (real DB, no mocked repos) coverage for {@link MasterDataService}'s new
 * single-protocol chakra/color self-serve CRUD: {@code addProtocolChakraColors} /
 * {@code updateProtocolChakraColor} / {@code deleteProtocolChakraColor} /
 * {@code getChakraColorGroupsForProtocol}. Same {@code @DataJpaTest} slice as {@link
 * ProtocolChakraColorRelationshipIntegrationTest} (that class's javadoc explains why: a
 * pre-existing, unrelated {@code @SpringBootTest} boot failure), with {@link MasterDataService}
 * constructed directly over the autowired repositories so the real V38 unique-constraint and
 * find-or-create behavior is exercised, not mocked.
 */
@DataJpaTest
@ActiveProfiles("test")
class ProtocolChakraColorCrudIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private ProtocolCatalogRepository protocolRepo;
    @Autowired private ChakraCatalogRepository chakraRepo;
    @Autowired private PranaColorCatalogRepository colorRepo;
    @Autowired private ProtocolChakraRepository protocolChakraRepo;
    @Autowired private ProtocolChakraColorRepository protocolChakraColorRepo;
    @Autowired private ProtocolColorMappingRepository mappingRepo;

    @MockBean private HealerCacheService healerCacheService;
    @MockBean private SecurityContextUtil securityContextUtil;

    private MasterDataService service;
    private UUID healerId;
    private ProtocolCatalog protocol;
    private ChakraCatalog crown;
    private PranaColorCatalog violet;
    private PranaColorCatalog green;

    @BeforeEach
    void setUp() {
        service = new MasterDataService(
                chakraRepo, colorRepo, mappingRepo, protocolRepo, protocolChakraRepo, protocolChakraColorRepo,
                securityContextUtil);

        healerId = UUID.randomUUID();
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);

        ProtocolCatalog p = new ProtocolCatalog();
        p.setName("Fever");
        p.setSourceBook("Test Book");
        p.setPageNumber(1);
        p.setProtocolType(ProtocolType.PROTOCOL);
        protocol = protocolRepo.save(p);

        ChakraCatalog c = new ChakraCatalog();
        c.setCode("CROWN_CRUD_T");
        c.setName("Crown Chakra");
        crown = chakraRepo.save(c);

        PranaColorCatalog v = new PranaColorCatalog();
        v.setCode("VIOLET_CRUD_T");
        v.setName("Violet");
        violet = colorRepo.save(v);

        PranaColorCatalog g = new PranaColorCatalog();
        g.setCode("GREEN_CRUD_T");
        g.setName("Green");
        green = colorRepo.save(g);
    }

    private ProtocolChakraColorEntry entry(UUID chakraId, List<UUID> cleansing, List<UUID> energising) {
        return new ProtocolChakraColorEntry(chakraId, cleansing, energising);
    }

    @Test
    @Transactional
    void addCreatesOwnLinkAndColors() {
        var result = service.addProtocolChakraColors(protocol.getId(),
                new ProtocolChakraColorAddRequest(List.of(entry(crown.getId(), List.of(violet.getId()), List.of(green.getId())))));
        entityManager.flush();
        entityManager.clear();

        assertThat(result).hasSize(1);
        ProtocolChakraColorGroup group = result.get(0);
        assertThat(group.chakraId()).isEqualTo(crown.getId());
        assertThat(group.healerId()).isEqualTo(healerId);
        assertThat(group.cleansingColorIds()).containsExactly(violet.getId());
        assertThat(group.energisingColorIds()).containsExactly(green.getId());
    }

    @Test
    @Transactional
    void addIsIdempotentForTheSameHealer_doesNotDuplicateColorsOrLinks() {
        var req = new ProtocolChakraColorAddRequest(
                List.of(entry(crown.getId(), List.of(violet.getId()), List.of())));
        service.addProtocolChakraColors(protocol.getId(), req);
        // Calling again with the same chakra+color must reuse the existing link, not violate
        // the (protocol_id, chakra_id, healer_id) constraint or duplicate the color row.
        var result = service.addProtocolChakraColors(protocol.getId(), req);
        entityManager.flush();
        entityManager.clear();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).cleansingColorIds()).containsExactly(violet.getId());
    }

    @Test
    @Transactional
    void addNeverTouchesAnExistingSystemRowForTheSameChakra() {
        // System row (healerId null) already links crown to this protocol.
        protocolChakraRepo.save(ProtocolChakra.builder().protocol(protocol).chakra(crown).build());
        entityManager.flush();

        var result = service.addProtocolChakraColors(protocol.getId(),
                new ProtocolChakraColorAddRequest(List.of(entry(crown.getId(), List.of(violet.getId()), List.of()))));
        entityManager.flush();
        entityManager.clear();

        // Both the system row and the healer's own new row must be visible.
        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(g -> assertThat(g.healerId()).isNull());
        assertThat(result).anySatisfy(g -> assertThat(g.healerId()).isEqualTo(healerId));
    }

    @Test
    @Transactional
    void getChakraColorGroupsForProtocol_excludesAnotherHealersOwnRow() {
        UUID otherHealerId = UUID.randomUUID();
        protocolChakraRepo.save(ProtocolChakra.builder().protocol(protocol).chakra(crown).healerId(otherHealerId).build());
        entityManager.flush();
        entityManager.clear();

        List<ProtocolChakraColorGroup> visible = service.getChakraColorGroupsForProtocol(protocol.getId());
        assertThat(visible).isEmpty();
    }

    @Test
    @Transactional
    void updateReplacesColorSet_addsRemovesAndKeepsUnchanged() {
        service.addProtocolChakraColors(protocol.getId(), new ProtocolChakraColorAddRequest(
                List.of(entry(crown.getId(), List.of(violet.getId(), green.getId()), List.of()))));
        entityManager.flush();
        entityManager.clear();

        UUID protocolChakraId = protocolChakraRepo
                .findByProtocol_IdAndChakra_IdAndHealerId(protocol.getId(), crown.getId(), healerId)
                .orElseThrow().getId();

        // Keep violet, drop green, no energising colors requested.
        var updated = service.updateProtocolChakraColor(protocol.getId(), protocolChakraId,
                new ProtocolChakraColorUpdateRequest(List.of(violet.getId()), List.of()), false);
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).hasSize(1);
        assertThat(updated.get(0).cleansingColorIds()).containsExactly(violet.getId());
        assertThat(updated.get(0).energisingColorIds()).isEmpty();
    }

    @Test
    @Transactional
    void updateByNonOwnerHealerIsRejected() {
        service.addProtocolChakraColors(protocol.getId(), new ProtocolChakraColorAddRequest(
                List.of(entry(crown.getId(), List.of(violet.getId()), List.of()))));
        entityManager.flush();
        entityManager.clear();
        UUID protocolChakraId = protocolChakraRepo
                .findByProtocol_IdAndChakra_IdAndHealerId(protocol.getId(), crown.getId(), healerId)
                .orElseThrow().getId();

        when(securityContextUtil.getCurrentHealerId()).thenReturn(UUID.randomUUID()); // a different healer

        assertThatThrownBy(() -> service.updateProtocolChakraColor(protocol.getId(), protocolChakraId,
                new ProtocolChakraColorUpdateRequest(List.of(), List.of()), false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @Transactional
    void updateOfSystemRowRequiresSuperAdmin() {
        ProtocolChakra systemLink = protocolChakraRepo.save(ProtocolChakra.builder().protocol(protocol).chakra(crown).build());
        entityManager.flush();

        assertThatThrownBy(() -> service.updateProtocolChakraColor(protocol.getId(), systemLink.getId(),
                new ProtocolChakraColorUpdateRequest(List.of(violet.getId()), List.of()), false))
                .isInstanceOf(AccessDeniedException.class);

        // SUPADM override succeeds.
        var result = service.updateProtocolChakraColor(protocol.getId(), systemLink.getId(),
                new ProtocolChakraColorUpdateRequest(List.of(violet.getId()), List.of()), true);
        assertThat(result.get(0).cleansingColorIds()).containsExactly(violet.getId());
    }

    @Test
    @Transactional
    void deleteRemovesLinkAndItsColors() {
        service.addProtocolChakraColors(protocol.getId(), new ProtocolChakraColorAddRequest(
                List.of(entry(crown.getId(), List.of(violet.getId()), List.of(green.getId())))));
        entityManager.flush();
        entityManager.clear();
        UUID protocolChakraId = protocolChakraRepo
                .findByProtocol_IdAndChakra_IdAndHealerId(protocol.getId(), crown.getId(), healerId)
                .orElseThrow().getId();

        service.deleteProtocolChakraColor(protocol.getId(), protocolChakraId, false);
        entityManager.flush();
        entityManager.clear();

        assertThat(service.getChakraColorGroupsForProtocol(protocol.getId())).isEmpty();
    }

    @Test
    @Transactional
    void deleteByNonOwnerHealerIsRejected() {
        service.addProtocolChakraColors(protocol.getId(), new ProtocolChakraColorAddRequest(
                List.of(entry(crown.getId(), List.of(violet.getId()), List.of()))));
        entityManager.flush();
        entityManager.clear();
        UUID protocolChakraId = protocolChakraRepo
                .findByProtocol_IdAndChakra_IdAndHealerId(protocol.getId(), crown.getId(), healerId)
                .orElseThrow().getId();

        when(securityContextUtil.getCurrentHealerId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.deleteProtocolChakraColor(protocol.getId(), protocolChakraId, false))
                .isInstanceOf(AccessDeniedException.class);
    }
}
