package com.pranicdoc.cph.masterdata.model;

import com.pranicdoc.cph.common.enums.ImportJobStatus;
import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.UUID;

/**
 * ProtocolImportJob — tracks one bulk protocol xlsx/csv import submitted by a
 * healer, so the HTTP request that submits it doesn't have to block for the
 * whole parse+upsert (see {@code ProtocolImportJobService}/{@code
 * ProtocolImportAsyncRunner}). Always healer-owned (no nullable "system"
 * row) — unlike the masterdata catalog tables, an import job isn't shared
 * data, just a record of one submission.
 */
@Entity
@Table(name = "protocol_import_job")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ProtocolImportJob extends BaseEntity {

    @Column(name = "healer_id", nullable = false, columnDefinition = "uuid")
    private UUID healerId;

    @Column(name = "original_filename")
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImportJobStatus status;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "created_count")
    private Integer createdCount;

    @Column(name = "updated_count")
    private Integer updatedCount;

    @Column(name = "skipped_count")
    private Integer skippedCount;

    /** JSON array of per-row error strings — see {@code ProtocolImportResult.errors()}. */
    @Column(name = "errors_json", columnDefinition = "text")
    private String errorsJson;

    /** Set only on FAILED — an exception that aborted the whole import, not a per-row error. */
    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
