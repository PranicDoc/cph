package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.common.dto.*;
import com.pranicdoc.cph.healing.model.*;
import com.pranicdoc.cph.patient.model.PatientMedicalHistory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Static utility class that maps cph JPA entities to canonical common-dtos records.
 *
 * <p>NOT a Spring @Component and NOT a MapStruct @Mapper — designed as a plain Java
 * utility class so it can be tested directly without a Spring context.
 *
 * <p>NAME COLLISION: cph has {@code com.pranicdoc.cph.healing.model.CaseClosure} (JPA entity)
 * AND common-dtos has {@code com.pranicdoc.common.dto.CaseClosure} (record). Both are on the
 * classpath. Fully-qualified names are used wherever both types appear in the same scope.
 */
public final class CphCaseMapper {

    private CphCaseMapper() {
        // static utility class — no instances
    }

    // -------------------------------------------------------------------------
    // Primary public entry points
    // -------------------------------------------------------------------------

    /**
     * Map a {@link HealingCase} entity to the canonical {@link com.pranicdoc.common.dto.CaseSnapshot} record.
     */
    public static com.pranicdoc.common.dto.CaseSnapshot toCaseSnapshot(HealingCase c) {
        return new com.pranicdoc.common.dto.CaseSnapshot(
                c.getId(),
                c.getPatientId(),
                c.getHealerId(),
                c.getMedicalCondition(),
                c.getLabFindings(),
                c.getComplementaryTreatments(),
                c.getComplementaryDetails(),
                c.getInfectiousIllness(),
                c.getInfectiousDetails(),
                c.getBasicBookPg(),
                c.getAdvancedBookPg(),
                c.getPsychotherapyPg(),
                c.getStatus() != null ? c.getStatus().name() : null,
                c.getCreatedAt(),
                c.getUpdatedAt(),
                toHealingDetailsSnapshot(c.getHealingDetails()),
                toMedicalSnapshot(c.getMedicalSnapshot()),
                toSessionSnapshotList(c.getSessions()),
                toSymptomSnapshotList(c.getSymptoms()),
                toMedicationSnapshotList(c.getMedications()),
                toCaseClosure(c.getClosure())
        );
    }

    /**
     * Map a cph {@link com.pranicdoc.cph.patient.model.Patient} entity to the canonical
     * {@link com.pranicdoc.common.dto.Patient} record.
     */
    public static com.pranicdoc.common.dto.Patient toPatientSnapshot(
            com.pranicdoc.cph.patient.model.Patient p) {
        PatientMedicalHistory h = p.getMedicalHistory();
        com.pranicdoc.common.dto.Patient.PatientMedicalHistory medHist = h == null ? null :
                new com.pranicdoc.common.dto.Patient.PatientMedicalHistory(
                        h.getGeneralHealth(),
                        h.getDiet(),
                        h.getExercise(),
                        h.getExerciseFrequency(),
                        h.getHypertension(),
                        h.getGiIssues(),
                        h.getStressDepressionAnxiety(),
                        h.getPregnant(),
                        h.getCancer(),
                        h.getCancerLocation(),
                        h.getCancerDetails(),
                        h.getAdditionalNotes()
                );
        return new com.pranicdoc.common.dto.Patient(
                p.getFirstName(),
                p.getLastName(),
                p.getDob(),
                p.getGender() != null
                        ? com.pranicdoc.common.dto.enums.Gender.valueOf(p.getGender().name())
                        : null,
                p.getMaritalStatus() != null
                        ? com.pranicdoc.common.dto.enums.MaritalStatus.valueOf(p.getMaritalStatus().name())
                        : null,
                p.getEmail(),
                p.getContact(),
                p.getOccupation(),
                p.getAddressLine1(),
                p.getAddressLine2(),
                p.getCity(),
                p.getState(),
                p.getZip(),
                medHist
        );
    }

    // -------------------------------------------------------------------------
    // Sub-mappers — package-private for testability
    // -------------------------------------------------------------------------

    /**
     * Map {@link com.pranicdoc.cph.healing.model.CaseClosure} entity to the
     * {@link com.pranicdoc.common.dto.CaseClosure} record.
     * Returns null when the entity argument is null (open cases have no closure).
     *
     * <p>Null-safe unboxing: entity has {@code Integer healingCount} and
     * {@code Boolean isHealingContinued}; the record has primitives.
     */
    static com.pranicdoc.common.dto.CaseClosure toCaseClosure(
            com.pranicdoc.cph.healing.model.CaseClosure e) {
        if (e == null) return null;
        return new com.pranicdoc.common.dto.CaseClosure(
                e.getClosureDate(),
                e.getAdditionalComments(),
                e.getHealingCount() != null ? e.getHealingCount() : 0,    // null-safe unboxing
                Boolean.TRUE.equals(e.getIsHealingContinued()),             // null-safe unboxing
                e.getLastFeedbackDate()
        );
        // healerSignature and closedBy are NOT in the common-dtos record — not mapped.
    }

    static HealingDetailsSnapshot toHealingDetailsSnapshot(HealingDetails d) {
        if (d == null) return null;
        List<TreatmentStepSnapshot> steps = d.getTreatmentSteps() == null
                ? Collections.emptyList()
                : d.getTreatmentSteps().stream().map(CphCaseMapper::toTreatmentStepSnapshot).toList();
        return new HealingDetailsSnapshot(
                d.getProtocolName(),
                d.getBookReference(),
                steps
        );
    }

    static TreatmentStepSnapshot toTreatmentStepSnapshot(TreatmentStep t) {
        // Record arg order: (int step, String tag, String sweepType, String notes, int cycles, List<String> targets)
        return new TreatmentStepSnapshot(
                t.getStep(),
                t.getTag(),
                t.getSweepType(),
                t.getNotes(),
                t.getCycles(),
                t.getTargets() != null ? t.getTargets() : Collections.emptyList()
        );
    }

    static MedicalSnapshot toMedicalSnapshot(CaseMedicalSnapshot m) {
        if (m == null) return null;
        return new MedicalSnapshot(
                m.getGeneralHealth(),
                m.getDiet(),
                m.getExercise(),
                m.getExerciseFrequency(),
                m.getHypertension(),
                m.getGiIssues(),
                m.getStressDepressionAnxiety(),
                m.getPregnant(),
                m.getCancer(),
                m.getCancerLocation(),
                m.getCancerDetails(),
                m.getAdditionalNotes()
        );
    }

    static List<SessionSnapshot> toSessionSnapshotList(List<HealingSession> sessions) {
        if (sessions == null) return Collections.emptyList();
        return sessions.stream()
                .sorted(Comparator.comparingInt(HealingSession::getSessionNumber))
                .map(CphCaseMapper::toSessionSnapshot)
                .toList();
    }

    static SessionSnapshot toSessionSnapshot(HealingSession s) {
        return new SessionSnapshot(
                s.getSessionNumber(),
                s.getSessionDate(),
                s.getStartTime(),
                s.getEndTime(),
                s.getStatus() != null ? s.getStatus().name() : null,
                toChakraScanSnapshotList(s.getScans()),
                toSymptomFeedbackSnapshotList(s.getSymptomFeedbacks()),
                toQualitativeFeedbackSnapshotList(s.getQualitativeFeedbacks()),
                toProtocolEntrySnapshotList(s.getProtocolEntries())
        );
    }

    static List<ChakraScanSnapshot> toChakraScanSnapshotList(List<ChakraScan> scans) {
        if (scans == null) return Collections.emptyList();
        return scans.stream().map(CphCaseMapper::toChakraScanSnapshot).toList();
    }

    static ChakraScanSnapshot toChakraScanSnapshot(ChakraScan scan) {
        return new ChakraScanSnapshot(
                scan.getScanType() != null ? scan.getScanType().name() : null,
                scan.getNotes(),
                toChakraReadingSnapshotList(scan.getReadings())
        );
    }

    static List<ChakraReadingSnapshot> toChakraReadingSnapshotList(List<ChakraReading> readings) {
        if (readings == null) return Collections.emptyList();
        return readings.stream().map(CphCaseMapper::toChakraReadingSnapshot).toList();
    }

    /**
     * Maps canonical chakra fields only — NEVER sizeReading or conditionReading.
     */
    static ChakraReadingSnapshot toChakraReadingSnapshot(ChakraReading r) {
        // Record arg order: (UUID chakraId, String chakraName, String energyLevel, String activationLevel, String notes)
        return new ChakraReadingSnapshot(
                r.getChakraId(),
                r.getChakraName(),       // canonical field — DB column chakra_name
                r.getEnergyLevel(),      // canonical field
                r.getActivationLevel(),  // canonical field
                r.getNotes()
        );
    }

    static List<SymptomSnapshot> toSymptomSnapshotList(List<CaseSymptom> symptoms) {
        if (symptoms == null) return Collections.emptyList();
        return symptoms.stream().map(CphCaseMapper::toSymptomSnapshot).toList();
    }

    static SymptomSnapshot toSymptomSnapshot(CaseSymptom s) {
        // Record arg order: (String symptomName, String description, Integer severity,
        //                    LocalDate addedDate, LocalDate resolvedDate, boolean resolved)
        return new SymptomSnapshot(
                s.getSymptomName(),
                s.getDescription(),
                s.getSeverity(),
                s.getAddedDate(),
                s.getResolvedDate(),
                s.isResolved()   // primitive boolean — uses isResolved(), not getResolved()
        );
    }

    static List<MedicationSnapshot> toMedicationSnapshotList(List<CaseMedication> medications) {
        if (medications == null) return Collections.emptyList();
        return medications.stream().map(CphCaseMapper::toMedicationSnapshot).toList();
    }

    static MedicationSnapshot toMedicationSnapshot(CaseMedication m) {
        // Record arg order: (String medicationName, String dosage, String frequency, String notes)
        return new MedicationSnapshot(
                m.getMedicationName(),
                m.getDosage(),
                m.getFrequency(),
                m.getNotes()
        );
    }

    static List<SymptomFeedbackSnapshot> toSymptomFeedbackSnapshotList(List<SymptomFeedback> feedbacks) {
        if (feedbacks == null) return Collections.emptyList();
        return feedbacks.stream().map(CphCaseMapper::toSymptomFeedbackSnapshot).toList();
    }

    static SymptomFeedbackSnapshot toSymptomFeedbackSnapshot(SymptomFeedback f) {
        // Record arg order: (UUID caseSymptomId, Integer rating, String notes)
        return new SymptomFeedbackSnapshot(
                f.getCaseSymptomId(),
                f.getRating(),
                f.getNotes()
        );
    }

    static List<QualitativeFeedbackSnapshot> toQualitativeFeedbackSnapshotList(
            List<QualitativeFeedback> feedbacks) {
        if (feedbacks == null) return Collections.emptyList();
        return feedbacks.stream().map(CphCaseMapper::toQualitativeFeedbackSnapshot).toList();
    }

    static QualitativeFeedbackSnapshot toQualitativeFeedbackSnapshot(QualitativeFeedback f) {
        // Record arg order: (String feedbackType, String feedbackText, String patientRemarks)
        return new QualitativeFeedbackSnapshot(
                f.getFeedbackType() != null ? f.getFeedbackType().name() : null,
                f.getFeedbackText(),
                f.getPatientRemarks()
        );
    }

    static List<ProtocolEntrySnapshot> toProtocolEntrySnapshotList(List<ProtocolEntry> entries) {
        if (entries == null) return Collections.emptyList();
        return entries.stream().map(CphCaseMapper::toProtocolEntrySnapshot).toList();
    }

    static ProtocolEntrySnapshot toProtocolEntrySnapshot(ProtocolEntry e) {
        // Record arg order: (UUID chakraId, String targetArea, String technique, String notes, List<ProtocolColorSnapshot> colors)
        return new ProtocolEntrySnapshot(
                e.getChakraId(),
                e.getTargetArea(),
                e.getTechnique(),
                e.getNotes(),
                toProtocolColorSnapshotList(e.getColors())
        );
    }

    static List<ProtocolColorSnapshot> toProtocolColorSnapshotList(List<ProtocolColor> colors) {
        if (colors == null) return Collections.emptyList();
        return colors.stream().map(CphCaseMapper::toProtocolColorSnapshot).toList();
    }

    static ProtocolColorSnapshot toProtocolColorSnapshot(ProtocolColor c) {
        // Record arg order: (UUID colorId, String usageType)
        return new ProtocolColorSnapshot(
                c.getColorId(),
                c.getUsageType() != null ? c.getUsageType().name() : null
        );
    }
}
