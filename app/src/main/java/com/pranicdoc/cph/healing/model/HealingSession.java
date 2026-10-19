package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.pranicdoc.cph.common.enums.SessionStatus;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HealingSession — a single healing session within a case.
 * Lifecycle: draft → auto-save per step → completed.
 */
@Entity
@Table(name = "healing_session")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class HealingSession extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "healing_case_id", nullable = false)
    @JsonBackReference
    private HealingCase healingCase;

    @Column(name = "session_number")
    private Integer sessionNumber;

    @Column(name = "session_date")
    private LocalDate sessionDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    /**
     * Idempotency-Key sent by the client on POST /sessions/case. A retried submit with the
     * same key returns this session instead of creating a duplicate. Not audited: it is
     * transport metadata, not clinical data.
     */
    @Column(name = "client_request_id", updatable = false)
    @NotAudited
    private UUID clientRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private SessionStatus status = SessionStatus.DRAFT;

    // --- Relationships ---

    @OneToMany(mappedBy = "healingSession", cascade = CascadeType.ALL)
    @Builder.Default
    @JsonManagedReference
    private List<ChakraScan> scans = new ArrayList<>(); // Pre + Post (max 2)

    @OneToMany(mappedBy = "healingSession", cascade = CascadeType.ALL)
    @Builder.Default
    @JsonManagedReference
    private List<SymptomFeedback> symptomFeedbacks = new ArrayList<>();

    @OneToMany(mappedBy = "healingSession", cascade = CascadeType.ALL)
    @Builder.Default
    @JsonManagedReference
    private List<QualitativeFeedback> qualitativeFeedbacks = new ArrayList<>();
}
