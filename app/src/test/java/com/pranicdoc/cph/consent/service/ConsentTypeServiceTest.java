package com.pranicdoc.cph.consent.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.consent.dto.ConsentTypeCreateRequest;
import com.pranicdoc.cph.consent.dto.ConsentTypeRenameRequest;
import com.pranicdoc.cph.consent.dto.ConsentTypeResponse;
import com.pranicdoc.cph.consent.model.ConsentType;
import com.pranicdoc.cph.consent.repository.ConsentTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentTypeServiceTest {

    @Mock private ConsentTypeRepository consentTypeRepository;

    @InjectMocks private ConsentTypeService consentTypeService;

    // --- fixtures ---

    private ConsentType systemType() {
        ConsentType t = ConsentType.builder()
                .key("terms-and-conditions")
                .label("Terms & Conditions")
                .mandatory(true)
                .active(true)
                .systemDefined(true)
                .build();
        t.setId(UUID.randomUUID());
        return t;
    }

    private ConsentType customType() {
        ConsentType t = ConsentType.builder()
                .key("marketing-consent")
                .label("Marketing Consent")
                .mandatory(false)
                .active(true)
                .systemDefined(false)
                .build();
        t.setId(UUID.randomUUID());
        return t;
    }

    // --- create ---

    @Test
    void create_slugifiesLabelAndDefaultsToNonMandatoryActiveCustom() {
        when(consentTypeRepository.existsByKey("marketing-consent")).thenReturn(false);
        ArgumentCaptor<ConsentType> captor = ArgumentCaptor.forClass(ConsentType.class);
        when(consentTypeRepository.save(captor.capture())).thenAnswer(inv -> {
            ConsentType t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        ConsentTypeResponse response = consentTypeService.create(new ConsentTypeCreateRequest("Marketing Consent"));

        ConsentType saved = captor.getValue();
        assertEquals("marketing-consent", saved.getKey());
        assertEquals("Marketing Consent", saved.getLabel());
        assertFalse(saved.isMandatory());
        assertTrue(saved.isActive());
        assertFalse(saved.isSystemDefined());

        assertEquals("marketing-consent", response.key());
        assertEquals("Marketing Consent", response.label());
        assertFalse(response.mandatory());
        assertTrue(response.active());
        assertFalse(response.systemDefined());
    }

    @Test
    void create_collidingSlug_throwsAndNeverSaves() {
        when(consentTypeRepository.existsByKey("marketing-consent")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> consentTypeService.create(new ConsentTypeCreateRequest("Marketing Consent")));

        assertEquals("A consent form with that name already exists. Please choose a different name.", ex.getMessage());
        verify(consentTypeRepository, never()).save(any());
    }

    @Test
    void create_collidingSlugAgainstInactiveType_throwsAndNeverSaves() {
        // existsByKey carries no active filter (per ConsentTypeRepository's own doc-comment) —
        // a slug collision against a deactivated row is rejected exactly like an active one (D-05).
        when(consentTypeRepository.existsByKey("legacy-form")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> consentTypeService.create(new ConsentTypeCreateRequest("Legacy Form")));

        verify(consentTypeRepository, never()).save(any());
    }

    @Test
    void create_symbolOnlyLabel_throwsBeforeCollisionCheck() {
        assertThrows(IllegalArgumentException.class,
                () -> consentTypeService.create(new ConsentTypeCreateRequest("!!! ??? ###")));

        verify(consentTypeRepository, never()).existsByKey(any());
        verify(consentTypeRepository, never()).save(any());
    }

    @Test
    void create_neverSetsSystemDefined() {
        when(consentTypeRepository.existsByKey("special-consent")).thenReturn(false);
        ArgumentCaptor<ConsentType> captor = ArgumentCaptor.forClass(ConsentType.class);
        when(consentTypeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        consentTypeService.create(new ConsentTypeCreateRequest("Special Consent"));

        assertFalse(captor.getValue().isSystemDefined());
    }

    // --- rename ---

    @Test
    void rename_changesLabelAndLeavesKeyUnchanged() {
        ConsentType existing = customType();
        when(consentTypeRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(consentTypeRepository.save(any(ConsentType.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsentTypeResponse response = consentTypeService.rename(existing.getId(), new ConsentTypeRenameRequest("New Label"));

        assertEquals("New Label", existing.getLabel());
        assertEquals("marketing-consent", existing.getKey());
        assertEquals("New Label", response.label());
        assertEquals("marketing-consent", response.key());
    }

    @Test
    void rename_systemDefinedType_isAllowed() {
        // D-07 cosmetic carve-out: rename is never guarded, even for a system-defined row.
        ConsentType existing = systemType();
        when(consentTypeRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(consentTypeRepository.save(any(ConsentType.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsentTypeResponse response = consentTypeService.rename(existing.getId(), new ConsentTypeRenameRequest("Renamed Terms"));

        assertEquals("Renamed Terms", response.label());
        assertEquals("terms-and-conditions", response.key());
    }

    // --- setActive: D-07 guard ---

    @Test
    void setActive_systemDefinedType_asAdmin_throwsAccessDeniedAndNeverSaves() {
        ConsentType existing = systemType();
        when(consentTypeRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class,
                () -> consentTypeService.setActive(existing.getId(), false, false));

        verify(consentTypeRepository, never()).save(any());
    }

    @Test
    void setActive_systemDefinedType_asSuperAdmin_succeeds() {
        ConsentType existing = systemType();
        when(consentTypeRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(consentTypeRepository.save(any(ConsentType.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsentTypeResponse response = consentTypeService.setActive(existing.getId(), false, true);

        assertFalse(response.active());
        assertFalse(existing.isActive());
    }

    // --- setMandatory: D-07 guard ---

    @Test
    void setMandatory_systemDefinedType_asAdmin_throwsAccessDeniedAndNeverSaves() {
        ConsentType existing = systemType();
        when(consentTypeRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class,
                () -> consentTypeService.setMandatory(existing.getId(), false, false));

        verify(consentTypeRepository, never()).save(any());
    }

    @Test
    void setMandatory_systemDefinedType_asSuperAdmin_succeeds() {
        ConsentType existing = systemType();
        when(consentTypeRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(consentTypeRepository.save(any(ConsentType.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsentTypeResponse response = consentTypeService.setMandatory(existing.getId(), false, true);

        assertFalse(response.mandatory());
        assertFalse(existing.isMandatory());
    }

    // --- setActive: D-02 no-soft-delete rule ---

    @Test
    void setActive_customType_deactivate_neverSetsIsDeleted() {
        ConsentType existing = customType();
        when(consentTypeRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(consentTypeRepository.save(any(ConsentType.class))).thenAnswer(inv -> inv.getArgument(0));

        consentTypeService.setActive(existing.getId(), false, false);

        assertFalse(existing.isActive());
        assertFalse(existing.isDeleted());
    }

    @Test
    void setActive_reactivate_setsActiveTrue() {
        ConsentType existing = customType();
        existing.setActive(false);
        when(consentTypeRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(consentTypeRepository.save(any(ConsentType.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsentTypeResponse response = consentTypeService.setActive(existing.getId(), true, false);

        assertTrue(response.active());
        assertTrue(existing.isActive());
    }

    // --- list ---

    @Test
    void list_excludesInactiveByDefault() {
        when(consentTypeRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(systemType()));

        List<ConsentTypeResponse> result = consentTypeService.list(false);

        assertEquals(1, result.size());
        verify(consentTypeRepository, never()).findAllByOrderByIdAsc();
    }

    @Test
    void list_includeInactive_returnsAll() {
        when(consentTypeRepository.findAllByOrderByIdAsc()).thenReturn(List.of(systemType(), customType()));

        List<ConsentTypeResponse> result = consentTypeService.list(true);

        assertEquals(2, result.size());
        verify(consentTypeRepository, never()).findAllByActiveTrueOrderByIdAsc();
    }

    // --- getById (exercised via rename, since getById itself is private) ---

    @Test
    void getById_unknownId_throwsResourceNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(consentTypeRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> consentTypeService.rename(unknownId, new ConsentTypeRenameRequest("New Label")));
    }

    // --- annotation contract assertions ---
    // Plain Mockito unit tests never run through the Spring AOP proxy, so annotation
    // presence is the only unit-level proof audit actions / cache eviction are wired.

    @Test
    void create_isAuditedWithCreateConsentTypeAction() throws NoSuchMethodException {
        Method method = ConsentTypeService.class.getMethod("create", ConsentTypeCreateRequest.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("CREATE_CONSENT_TYPE", auditable.action());
    }

    @Test
    void rename_isAuditedWithUpdateConsentTypeAction() throws NoSuchMethodException {
        Method method = ConsentTypeService.class.getMethod("rename", UUID.class, ConsentTypeRenameRequest.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("UPDATE_CONSENT_TYPE", auditable.action());
    }

    @Test
    void setMandatory_isAuditedWithUpdateConsentTypeMandatoryAction() throws NoSuchMethodException {
        Method method = ConsentTypeService.class.getMethod("setMandatory", UUID.class, boolean.class, boolean.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("UPDATE_CONSENT_TYPE_MANDATORY", auditable.action());
    }

    @Test
    void setActive_isAuditedWithUpdateConsentTypeActiveAction() throws NoSuchMethodException {
        Method method = ConsentTypeService.class.getMethod("setActive", UUID.class, boolean.class, boolean.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("UPDATE_CONSENT_TYPE_ACTIVE", auditable.action());
    }

    @Test
    void setActive_isAnnotatedWithCacheEvictOnActiveVersionKey() throws NoSuchMethodException {
        Method method = ConsentTypeService.class.getMethod("setActive", UUID.class, boolean.class, boolean.class);

        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);

        assertNotNull(cacheEvict);
        assertTrue(Arrays.asList(cacheEvict.value()).contains("policy.active-version"));
        assertEquals("'all'", cacheEvict.key());
    }
}
