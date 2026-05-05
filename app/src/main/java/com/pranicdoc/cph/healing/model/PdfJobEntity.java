package com.pranicdoc.cph.healing.model;

import com.pranicdoc.cph.common.enums.JobStatus;
import com.pranicdoc.cph.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "pdf_job")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class PdfJobEntity extends BaseEntity {
    private String jobId;
    private UUID healingCaseId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "healing_case_data", nullable = false, columnDefinition = "jsonb")
    private String healingCaseData;
    private int retryCount;
    private int documentVersion;
    @Enumerated(EnumType.STRING)
    private JobStatus status;
    private String fileUrl;

    public static PdfJobEntity create(String jobId, UUID healingCaseId, String healingCaseData) {
        PdfJobEntity pdfJobEntity = new PdfJobEntity();
        pdfJobEntity.setJobId(jobId);
        pdfJobEntity.setHealingCaseId(healingCaseId);
        pdfJobEntity.setHealingCaseData(healingCaseData);
        pdfJobEntity.setStatus(JobStatus.DRAFT);
        pdfJobEntity.setDocumentVersion(1);
        pdfJobEntity.setRetryCount(0);
        return pdfJobEntity;
    }
}
