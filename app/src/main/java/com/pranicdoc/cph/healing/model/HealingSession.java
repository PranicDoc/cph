package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.pranicdoc.cph.common.enums.SessionStatus;
import com.pranicdoc.cph.common.model.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * HealingSession — a single healing session within a case.
 * Lifecycle: draft → auto-save per step → completed.
 */
@Entity
@Table(name = "healing_session")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private SessionStatus status = SessionStatus.DRAFT;

    // --- Relationships ---

    @OneToMany(mappedBy = "healingSession", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ChakraScan> scans = new ArrayList<>(); // Pre + Post (max 2)

    @OneToMany(mappedBy = "healingSession", cascade = CascadeType.ALL)
    @Builder.Default
    private List<SymptomFeedback> symptomFeedbacks = new ArrayList<>();

    @OneToMany(mappedBy = "healingSession", cascade = CascadeType.ALL)
    @Builder.Default
    private List<QualitativeFeedback> qualitativeFeedbacks = new ArrayList<>();

    @OneToMany(mappedBy = "healingSession", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ProtocolEntry> protocolEntries = new ArrayList<>();
}
