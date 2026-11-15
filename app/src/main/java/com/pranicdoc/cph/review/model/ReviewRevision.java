package com.pranicdoc.cph.review.model;

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
 * One submitted version of a case PDF. {@code fileUrl} is the pinned copy
 * ({@code review/{submissionId}/r{n}.pdf}), never the pdf_job key, which a regenerated PDF overwrites.
 * {@code reviewFileUrl} is reserved for a future masked copy; the viewer serves it when present.
 */
@Entity
@Table(name = "review_revision")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ReviewRevision extends BaseEntity {

    @Column(name = "submission_id", nullable = false, updatable = false)
    private UUID submissionId;

    @Column(name = "revision_no", nullable = false, updatable = false)
    private int revisionNo;

    @Column(name = "pdf_job_id", updatable = false)
    private UUID pdfJobId;

    @Column(name = "file_url", nullable = false, updatable = false)
    private String fileUrl;

    @Column(name = "review_file_url")
    private String reviewFileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "submitted_via", nullable = false, updatable = false)
    private SubmittedVia submittedVia;

    /** SHA-256 hex of the case data this PDF was built from; null on revisions from before it existed. */
    @Column(name = "snapshot_hash", length = 64, updatable = false)
    private String snapshotHash;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;
}
