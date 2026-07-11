package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.common.dto.*;
import com.pranicdoc.cph.healing.model.*;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository;
import com.pranicdoc.cph.healing.service.impl.HealerProtocolUnderstandingService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class CphCaseMapper {

    @Autowired
    protected HealerProtocolUnderstandingRepository hpuRepository;

    @Autowired
    protected HealerProtocolUnderstandingService hpuService;

    // ── Primary entry points ──────────────────────────────────────────────────

    @Mapping(target = "caseClosure", source = "closure")
    @Mapping(target = "sessions",    qualifiedByName = "toSortedSessions")
    public abstract com.pranicdoc.common.dto.CaseSnapshot toCaseSnapshot(HealingCase c);

    @Mapping(target = "patientMedicalHistory", source = "medicalHistory")
    @Mapping(target = "gender",        qualifiedByName = "toGender")
    @Mapping(target = "maritalStatus", qualifiedByName = "toMaritalStatus")
    public abstract com.pranicdoc.common.dto.Patient toPatientSnapshot(
            com.pranicdoc.cph.patient.model.Patient p);

    // ── Sub-mappers ───────────────────────────────────────────────────────────

    @Mapping(target = "healingCount",       nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
    @Mapping(target = "isHealingContinued", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
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

    // ── Sorted sessions with HPU enrichment ──────────────────────────────────

    @Named("toSortedSessions")
    public List<SessionSnapshot> toSortedSessions(List<HealingSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return Collections.emptyList();
        // D-25/INFRA-01: hpuRepository.findByHealingCaseId(...) is keyed by
        // healingCaseId, not sessionId — every session in this list resolves
        // to the SAME row. Hoisted out of the per-session map() below: this
        // was a confirmed N+1 (one identical query per session) on the
        // PDF-generation snapshot load path.
        HealerProtocolUnderstandingSnapshot hpu = hpuRepository
                .findByHealingCaseId(sessions.get(0).getHealingCase().getId())
                .map(this::toHpuSnapshot)
                .orElse(null);
        return sessions.stream()
                .sorted(Comparator.comparingInt(HealingSession::getSessionNumber))
                .map(s -> toSessionSnapshot(s).withHealerProtocolUnderstanding(hpu))
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
        return ms != null
                ? com.pranicdoc.common.dto.enums.MaritalStatus.valueOf(ms.name())
                : null;
    }

    // ── HPU snapshot builder ──────────────────────────────────────────────────

    private HealerProtocolUnderstandingSnapshot toHpuSnapshot(
            com.pranicdoc.cph.healing.model.HealerProtocolUnderstanding hpu) {
        return new HealerProtocolUnderstandingSnapshot(
                hpu.getHealerId(),
                hpu.getClinicId(),
                0,
                hpu.getCompletionDate(),
                hpu.getProtocolName(),
                hpuService.deserializeColors(hpu.getCleansingColorsJson()),
                hpuService.deserializeColors(hpu.getEnergisingColorsJson()),
                hpuService.deserializeChakraReadings(hpu.getChakraReadingsJson())
        );
    }
}
