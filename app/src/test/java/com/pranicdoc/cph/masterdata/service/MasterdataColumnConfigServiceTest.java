package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.masterdata.dto.ColumnConfigCreateRequest;
import com.pranicdoc.cph.masterdata.dto.ColumnConfigResponse;
import com.pranicdoc.cph.masterdata.dto.ColumnConfigUpdateRequest;
import com.pranicdoc.cph.masterdata.model.ColumnDataType;
import com.pranicdoc.cph.masterdata.model.MasterdataColumnConfig;
import com.pranicdoc.cph.masterdata.repository.MasterdataColumnConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasterdataColumnConfigServiceTest {

    @Mock private MasterdataColumnConfigRepository masterdataColumnConfigRepository;

    @InjectMocks private MasterdataColumnConfigService masterdataColumnConfigService;

    // --- fixtures ---

    private MasterdataColumnConfig systemRow() {
        MasterdataColumnConfig c = MasterdataColumnConfig.builder()
                .masterdataType(MasterdataType.PROTOCOL)
                .columnName("Protocol/Technique Name")
                .targetField("name")
                .required(true)
                .dataType(ColumnDataType.STRING)
                .systemDefined(true)
                .build();
        c.setId(UUID.randomUUID());
        return c;
    }

    private MasterdataColumnConfig customRow() {
        MasterdataColumnConfig c = MasterdataColumnConfig.builder()
                .masterdataType(MasterdataType.CHAKRA)
                .columnName("Note")
                .targetField("note")
                .required(false)
                .dataType(ColumnDataType.STRING)
                .systemDefined(false)
                .build();
        c.setId(UUID.randomUUID());
        return c;
    }

    // --- create ---

    @Test
    void create_neverPersistsSystemDefinedTrue_evenThoughRequestCannotSetIt() {
        // Structural proof: ColumnConfigCreateRequest has no systemDefined accessor at all —
        // this test proves the service's own hardcoding on top of that structural guarantee.
        when(masterdataColumnConfigRepository.existsByMasterdataTypeAndColumnName(
                MasterdataType.CHAKRA, "Note")).thenReturn(false);
        ArgumentCaptor<MasterdataColumnConfig> captor = ArgumentCaptor.forClass(MasterdataColumnConfig.class);
        when(masterdataColumnConfigRepository.save(captor.capture())).thenAnswer(inv -> {
            MasterdataColumnConfig c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        ColumnConfigResponse response = masterdataColumnConfigService.create(
                new ColumnConfigCreateRequest(MasterdataType.CHAKRA, "Note", "note", false, ColumnDataType.STRING));

        assertFalse(captor.getValue().isSystemDefined());
        assertFalse(response.systemDefined());
        assertEquals(MasterdataType.CHAKRA, captor.getValue().getMasterdataType());
        assertEquals("Note", captor.getValue().getColumnName());
        assertEquals("note", captor.getValue().getTargetField());
        assertFalse(captor.getValue().isRequired());
        assertEquals(ColumnDataType.STRING, captor.getValue().getDataType());
    }

    @Test
    void create_collidingColumnName_throwsAndNeverSaves() {
        when(masterdataColumnConfigRepository.existsByMasterdataTypeAndColumnName(
                MasterdataType.PROTOCOL, "Book")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> masterdataColumnConfigService.create(
                        new ColumnConfigCreateRequest(MasterdataType.PROTOCOL, "Book", "sourceBook", true, ColumnDataType.STRING)));

        assertTrue(ex.getMessage().contains("Book"));
        verify(masterdataColumnConfigRepository, never()).save(any());
    }

    @Test
    void create_dbLevelUniqueViolation_translatesToIllegalArgumentException() {
        // CR-01/WR-01: the proactive existsBy... check is soft-delete-aware and can pass even
        // when a concurrent create races this one and wins at the DB layer first. The partial
        // unique index (masterdata_type, column_name) WHERE is_deleted = FALSE then rejects
        // this save — the raw DataIntegrityViolationException must be translated, not leaked
        // as an unhandled 500.
        when(masterdataColumnConfigRepository.existsByMasterdataTypeAndColumnName(
                MasterdataType.CHAKRA, "Note")).thenReturn(false);
        when(masterdataColumnConfigRepository.save(any(MasterdataColumnConfig.class)))
                .thenThrow(new DataIntegrityViolationException("uq_masterdata_column_config_type_column"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> masterdataColumnConfigService.create(
                        new ColumnConfigCreateRequest(MasterdataType.CHAKRA, "Note", "note", false, ColumnDataType.STRING)));

        assertTrue(ex.getMessage().contains("Note"));
        assertInstanceOf(DataIntegrityViolationException.class, ex.getCause());
    }

    // --- update ---

    @Test
    void update_renameToCollidingColumnName_throwsAndNeverSaves() {
        MasterdataColumnConfig existing = customRow();
        when(masterdataColumnConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(masterdataColumnConfigRepository.existsByMasterdataTypeAndColumnName(
                MasterdataType.CHAKRA, "Code")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> masterdataColumnConfigService.update(existing.getId(),
                        new ColumnConfigUpdateRequest("Code", "code", true, ColumnDataType.STRING)));

        verify(masterdataColumnConfigRepository, never()).save(any());
    }

    @Test
    void update_dbLevelUniqueViolation_translatesToIllegalArgumentException() {
        MasterdataColumnConfig existing = customRow();
        when(masterdataColumnConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(masterdataColumnConfigRepository.existsByMasterdataTypeAndColumnName(
                MasterdataType.CHAKRA, "Code")).thenReturn(false);
        when(masterdataColumnConfigRepository.save(any(MasterdataColumnConfig.class)))
                .thenThrow(new DataIntegrityViolationException("uq_masterdata_column_config_type_column"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> masterdataColumnConfigService.update(existing.getId(),
                        new ColumnConfigUpdateRequest("Code", "code", true, ColumnDataType.STRING)));

        assertTrue(ex.getMessage().contains("Code"));
        assertInstanceOf(DataIntegrityViolationException.class, ex.getCause());
    }

    @Test
    void update_noNameChangeOrNoCollision_appliesFieldsAndNeverTouchesTypeOrSystemDefined() {
        MasterdataColumnConfig existing = customRow();
        when(masterdataColumnConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(masterdataColumnConfigRepository.save(any(MasterdataColumnConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ColumnConfigResponse response = masterdataColumnConfigService.update(existing.getId(),
                new ColumnConfigUpdateRequest("Note", "noteField", true, ColumnDataType.NUMBER));

        assertEquals("Note", existing.getColumnName());
        assertEquals("noteField", existing.getTargetField());
        assertTrue(existing.isRequired());
        assertEquals(ColumnDataType.NUMBER, existing.getDataType());
        assertEquals(MasterdataType.CHAKRA, existing.getMasterdataType());
        assertFalse(existing.isSystemDefined());

        assertEquals("noteField", response.targetField());
        assertEquals(MasterdataType.CHAKRA, response.masterdataType());
        assertFalse(response.systemDefined());
    }

    @Test
    void update_renameToSameName_isAllowed_noCollisionCheckAgainstItself() {
        MasterdataColumnConfig existing = customRow();
        when(masterdataColumnConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(masterdataColumnConfigRepository.save(any(MasterdataColumnConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        masterdataColumnConfigService.update(existing.getId(),
                new ColumnConfigUpdateRequest("Note", "note", false, ColumnDataType.STRING));

        verify(masterdataColumnConfigRepository, never())
                .existsByMasterdataTypeAndColumnName(any(), any());
    }

    // --- remove ---

    @Test
    void remove_softDeletesAndSaves_neverHardDeletes() {
        MasterdataColumnConfig existing = customRow();
        when(masterdataColumnConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(masterdataColumnConfigRepository.save(any(MasterdataColumnConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        masterdataColumnConfigService.remove(existing.getId());

        assertTrue(existing.isDeleted());
        verify(masterdataColumnConfigRepository).save(existing);
        verify(masterdataColumnConfigRepository, never()).delete(any());
        verify(masterdataColumnConfigRepository, never()).deleteById(any());
    }

    @Test
    void remove_neverMutatesAnyFieldOtherThanIsDeleted() {
        MasterdataColumnConfig existing = systemRow();
        when(masterdataColumnConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(masterdataColumnConfigRepository.save(any(MasterdataColumnConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        masterdataColumnConfigService.remove(existing.getId());

        assertEquals(MasterdataType.PROTOCOL, existing.getMasterdataType());
        assertEquals("Protocol/Technique Name", existing.getColumnName());
        assertEquals("name", existing.getTargetField());
        assertTrue(existing.isRequired());
        assertEquals(ColumnDataType.STRING, existing.getDataType());
        assertTrue(existing.isSystemDefined());
    }

    // --- getById (exercised via update/remove, since getById itself is private) ---

    @Test
    void update_unknownId_throwsResourceNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(masterdataColumnConfigRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> masterdataColumnConfigService.update(unknownId,
                        new ColumnConfigUpdateRequest("Note", "note", false, ColumnDataType.STRING)));
    }

    @Test
    void remove_unknownId_throwsResourceNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(masterdataColumnConfigRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> masterdataColumnConfigService.remove(unknownId));
    }

    // --- list ---

    @Test
    void list_returnsAllRowsForType_systemAndCustomAlike() {
        when(masterdataColumnConfigRepository.findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PROTOCOL))
                .thenReturn(List.of(systemRow(), MasterdataColumnConfig.builder()
                        .masterdataType(MasterdataType.PROTOCOL)
                        .columnName("Custom")
                        .targetField("custom")
                        .required(false)
                        .dataType(ColumnDataType.STRING)
                        .systemDefined(false)
                        .build()));

        List<ColumnConfigResponse> result = masterdataColumnConfigService.list(MasterdataType.PROTOCOL);

        assertEquals(2, result.size());
    }

    // --- annotation contract assertions ---
    // Plain Mockito unit tests never run through the Spring AOP proxy, so annotation
    // presence is the only unit-level proof audit actions are wired.

    @Test
    void create_isAuditedWithCreateMasterdataColumnConfigAction() throws NoSuchMethodException {
        Method method = MasterdataColumnConfigService.class.getMethod("create", ColumnConfigCreateRequest.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("CREATE_MASTERDATA_COLUMN_CONFIG", auditable.action());
    }

    @Test
    void update_isAuditedWithUpdateMasterdataColumnConfigAction() throws NoSuchMethodException {
        Method method = MasterdataColumnConfigService.class.getMethod("update", UUID.class, ColumnConfigUpdateRequest.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("UPDATE_MASTERDATA_COLUMN_CONFIG", auditable.action());
    }

    @Test
    void remove_isAuditedWithDeleteMasterdataColumnConfigAction() throws NoSuchMethodException {
        Method method = MasterdataColumnConfigService.class.getMethod("remove", UUID.class);

        Auditable auditable = method.getAnnotation(Auditable.class);

        assertNotNull(auditable);
        assertEquals("DELETE_MASTERDATA_COLUMN_CONFIG", auditable.action());
    }
}
