package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.common.dto.*;
import com.pranicdoc.cph.common.enums.FeedbackType;
import com.pranicdoc.cph.healing.model.*;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository;
import com.pranicdoc.cph.healing.service.impl.HealerProtocolUnderstandingService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class CphCaseMapper {

    @Autowired
    protected HealerProtocolUnderstandingRepository hpuRepository;

    @Autowired
    protected HealerProtocolUnderstandingService hpuService;

    // ── Primary entry points ──────────────────────────────────────────────────

    @Mapping(target = "caseClosure", source = "closure")
    @Mapping(target = "sessions",    qualifiedByName = "toSortedSessions")
    @Mapping(target = "healerProtocolUnderstanding", expression = "java(caseProtocolUnderstanding(c))")
    @Mapping(target = "declarationDate", expression = "java(declarationDate(c))")
    public abstract com.pranicdoc.common.dto.CaseSnapshot toCaseSnapshot(HealingCase c);

    @Mapping(target = "patientMedicalHistory", source = "medicalHistory")
    @Mapping(target = "gender",        qualifiedByName = "toGender")
    @Mapping(target = "maritalStatus", qualifiedByName = "toMaritalStatus")
    public abstract com.pranicdoc.common.dto.Patient toPatientSnapshot(
            com.pranicdoc.cph.patient.model.Patient p);

    // ── Sub-mappers ───────────────────────────────────────────────────────────

    @Mapping(target = "healingCount",       nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
    @Mapping(target = "isHealingContinued", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
    @Mapping(target = "lastFeedbackDate",   expression = "java(latestFeedbackDate(e))")
    public abstract com.pranicdoc.common.dto.CaseClosure toCaseClosure(
            com.pranicdoc.cph.healing.model.CaseClosure e);

    public abstract com.pranicdoc.common.dto.Patient.PatientMedicalHistory toPatientMedicalHistory(
            com.pranicdoc.cph.patient.model.PatientMedicalHistory h);

    public abstract HealingDetailsSnapshot toHealingDetailsSnapshot(HealingDetails d);

    @Mapping(target = "targets", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
    public abstract TreatmentStepSnapshot toTreatmentStepSnapshot(TreatmentStep t);

    public abstract MedicalSnapshot         toMedicalSnapshot(CaseMedicalSnapshot m);

    @Mapping(target = "healerProtocolUnderstanding", ignore = true)
    public abstract SessionSnapshot         toSessionSnapshot(HealingSession s);

    public abstract ChakraScanSnapshot      toChakraScanSnapshot(ChakraScan scan);
    public abstract ChakraReadingSnapshot   toChakraReadingSnapshot(ChakraReading r);
    public abstract SymptomSnapshot         toSymptomSnapshot(CaseSymptom s);
    public abstract MedicationSnapshot      toMedicationSnapshot(CaseMedication m);
    public abstract SymptomFeedbackSnapshot toSymptomFeedbackSnapshot(SymptomFeedback f);

    @Mapping(target = "feedbackType", expression = "java(f.getFeedbackType() != null ? f.getFeedbackType().name() : null)")
    public abstract QualitativeFeedbackSnapshot toQualitativeFeedbackSnapshot(QualitativeFeedback f);

    @Mapping(target = "usageType", expression = "java(c.getUsageType() != null ? c.getUsageType().name() : null)")
    public abstract ProtocolColorSnapshot toProtocolColorSnapshot(ProtocolColor c);

    /**
     * The date printed as the last feedback: the final feedback's own date (the healer can set it),
     * else the latest of all feedback on the case. The value stored on the closure is set when the
     * case is closed, before the final feedback exists, so it is stale (or null) by the time the PDF
     * is built; fall back to it only when no feedback carries a date.
     */
    protected LocalDate latestFeedbackDate(com.pranicdoc.cph.healing.model.CaseClosure closure) {
        HealingCase healingCase = closure.getHealingCase();
        if (healingCase == null || healingCase.getSessions() == null) return closure.getLastFeedbackDate();
        return healingCase.getSessions().stream()
                .flatMap(s -> s.getQualitativeFeedbacks().stream())
                .filter(q -> q.getFeedbackType() == FeedbackType.FINAL)
                .map(QualitativeFeedback::getHealingDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElseGet(() -> healingCase.getSessions().stream()
                        .flatMap(s -> Stream.concat(
                                s.getQualitativeFeedbacks().stream().map(QualitativeFeedback::getHealingDate),
                                s.getSymptomFeedbacks().stream().map(SymptomFeedback::getHealingDate)))
                        .filter(Objects::nonNull)
                        .max(LocalDate::compareTo)
                        .orElse(closure.getLastFeedbackDate()));
    }

    /** The date the declaration was signed; null for a case with no declaration or no stored date. */
    protected LocalDate declarationDate(HealingCase c) {
        return c.getDeclaration() == null ? null : DeclarationDates.toDate(c.getDeclaration().getSignedAt());
    }

    // ── Sorted sessions with HPU enrichment ──────────────────────────────────

    /**
     * The healer's protocol understanding is one per case (it is keyed by the case), so it goes on the
     * case snapshot next to the closure instead of being copied onto every session.
     */
    protected HealerProtocolUnderstandingSnapshot caseProtocolUnderstanding(HealingCase c) {
        if (c == null || c.getId() == null) return null;
        return hpuRepository.findByHealingCaseId(c.getId()).map(this::toHpuSnapshot).orElse(null);
    }

    @Named("toSortedSessions")
    public List<SessionSnapshot> toSortedSessions(List<HealingSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return Collections.emptyList();
        return sessions.stream()
                .sorted(Comparator.comparingInt(HealingSession::getSessionNumber))
                .map(this::toSessionSnapshot)
                .toList();
    }

    // ── Enum converters ───────────────────────────────────────────────────────

    @Named("toGender")
    public com.pranicdoc.common.dto.enums.Gender toGender(
            com.pranicdoc.cph.common.enums.Gender g) {
        return g != null ? com.pranicdoc.common.dto.enums.Gender.valueOf(g.name()) : null;
    }

    @Named("toMaritalStatus")
    public com.pranicdoc.common.dto.enums.MaritalStatus toMaritalStatus(
            com.pranicdoc.cph.common.enums.MaritalStatus ms) {
        if (ms == null) return null;
        // common-dtos (and the PDF template) only know SINGLE/MARRIED/OTHERS.
        return switch (ms) {
            case SINGLE -> com.pranicdoc.common.dto.enums.MaritalStatus.SINGLE;
            case MARRIED -> com.pranicdoc.common.dto.enums.MaritalStatus.MARRIED;
            case DIVORCED, WIDOWED, OTHERS -> com.pranicdoc.common.dto.enums.MaritalStatus.OTHERS;
        };
    }

    // ── HPU snapshot builder ──────────────────────────────────────────────────

    private HealerProtocolUnderstandingSnapshot toHpuSnapshot(
            com.pranicdoc.cph.healing.model.HealerProtocolUnderstanding hpu) {
        return new HealerProtocolUnderstandingSnapshot(
                hpu.getHealerId(),
                hpu.getHealerId(),
                0,
                hpu.getCompletionDate(),
                hpu.getProtocolName(),
                hpuService.deserializeColors(hpu.getCleansingColorsJson()),
                hpuService.deserializeColors(hpu.getEnergisingColorsJson()),
                hpuService.deserializeChakraReadings(hpu.getChakraReadingsJson())
        );
    }
}
