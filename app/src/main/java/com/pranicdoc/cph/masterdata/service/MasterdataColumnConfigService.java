package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.masterdata.dto.ColumnConfigCreateRequest;
import com.pranicdoc.cph.masterdata.dto.ColumnConfigResponse;
import com.pranicdoc.cph.masterdata.dto.ColumnConfigUpdateRequest;
import com.pranicdoc.cph.masterdata.model.MasterdataColumnConfig;
import com.pranicdoc.cph.masterdata.repository.MasterdataColumnConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * MasterdataColumnConfigService — CRUD business logic for the {@code
 * masterdata_column_config} registry (Phase 01, masterdata-import-redesign,
 * MDIMPORT-01/02/03).
 *
 * <p>Per D-10, every handler on {@code MasterdataColumnConfigController} is
 * gated on its own matching grantable verb (READ/CREATE/UPDATE/DELETE) — no
 * caller reaches this service without already holding the exact permission
 * their action requires. Unlike {@code ConsentTypeService}'s {@code
 * setMandatory}/{@code setActive}, there is therefore no {@code
 * isSuperAdmin} parameter threaded through any method here: there is no
 * system-row override to guard.
 *
 * <ul>
 *   <li>{@code create} hardcodes {@code systemDefined = false} on every row
 *       regardless of request content (T-01-04) — the create-request DTO
 *       structurally excludes the field, so this is belt-and-suspenders,
 *       not the only defense.</li>
 *   <li>{@code update} never mutates {@code masterdataType} or {@code
 *       systemDefined} — the update-request DTO structurally excludes
 *       both.</li>
 *   <li>{@code remove} uses {@link MasterdataColumnConfig#softDelete()}
 *       (inherited from {@code BaseEntity}) — real removal via {@code
 *       is_deleted}, never a hard delete (Pitfall 1).</li>
 *   <li>{@code list} returns every row for a type, system-defined and
 *       custom alike — no active-style filtering (D-10, RESEARCH.md Open
 *       Question 2's "allow unrestricted delete for Phase 1"
 *       recommendation).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MasterdataColumnConfigService {

    private final MasterdataColumnConfigRepository masterdataColumnConfigRepository;

    @Transactional(readOnly = true)
    public List<ColumnConfigResponse> list(MasterdataType type) {
        return masterdataColumnConfigRepository.findAllByMasterdataTypeOrderByColumnNameAsc(type)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Auditable(action = "CREATE_MASTERDATA_COLUMN_CONFIG")
    public ColumnConfigResponse create(ColumnConfigCreateRequest request) {
        if (masterdataColumnConfigRepository.existsByMasterdataTypeAndColumnName(
                request.masterdataType(), request.columnName())) {
            throw new IllegalArgumentException(
                    "A column named '" + request.columnName() + "' already exists for this masterdata type.");
        }

        MasterdataColumnConfig config = MasterdataColumnConfig.builder()
                .masterdataType(request.masterdataType())
                .columnName(request.columnName())
                .targetField(request.targetField())
                .required(request.required())
                .dataType(request.dataType())
                .systemDefined(false)
                .build();

        return saveOrTranslateCollision(config, request.columnName());
    }

    @Auditable(action = "UPDATE_MASTERDATA_COLUMN_CONFIG")
    public ColumnConfigResponse update(UUID id, ColumnConfigUpdateRequest request) {
        MasterdataColumnConfig existing = getById(id);

        if (!request.columnName().equals(existing.getColumnName())
                && masterdataColumnConfigRepository.existsByMasterdataTypeAndColumnName(
                        existing.getMasterdataType(), request.columnName())) {
            throw new IllegalArgumentException(
                    "A column named '" + request.columnName() + "' already exists for this masterdata type.");
        }

        existing.setColumnName(request.columnName());
        existing.setTargetField(request.targetField());
        existing.setRequired(request.required());
        existing.setDataType(request.dataType());

        return saveOrTranslateCollision(existing, request.columnName());
    }

    /**
     * WR-01: the proactive {@code existsByMasterdataTypeAndColumnName} check above is
     * soft-delete-aware, but it cannot fully replace a DB-level constraint (a concurrent
     * create/update racing this one can still collide between the check and the write).
     * Translate that race — or any other unique-index violation — into the same clean
     * {@link IllegalArgumentException} the proactive check throws, instead of letting a raw
     * {@link DataIntegrityViolationException} surface as an unhandled 500.
     */
    private ColumnConfigResponse saveOrTranslateCollision(MasterdataColumnConfig config, String columnName) {
        try {
            return toResponse(masterdataColumnConfigRepository.save(config));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(
                    "A column named '" + columnName + "' already exists for this masterdata type.", e);
        }
    }

    @Auditable(action = "DELETE_MASTERDATA_COLUMN_CONFIG")
    public void remove(UUID id) {
        MasterdataColumnConfig config = getById(id);
        config.softDelete();
        masterdataColumnConfigRepository.save(config);
    }

    private MasterdataColumnConfig getById(UUID id) {
        return masterdataColumnConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MasterdataColumnConfig", id));
    }

    private ColumnConfigResponse toResponse(MasterdataColumnConfig c) {
        return new ColumnConfigResponse(
                c.getId(),
                c.getMasterdataType(),
                c.getColumnName(),
                c.getTargetField(),
                c.isRequired(),
                c.getDataType(),
                c.isSystemDefined(),
                c.getCreatedAt());
    }
}
