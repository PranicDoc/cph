package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.common.dto.*;
import com.pranicdoc.cph.healing.model.*;
import org.mapstruct.*;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CphCaseMapper {

    // ── Primary entry points ──────────────────────────────────────────────────

    @Mapping(target = "caseClosure", source = "closure")
    @Mapping(target = "sessions",    qualifiedByName = "toSortedSessions")
    com.pranicdoc.common.dto.CaseSnapshot toCaseSnapshot(HealingCase c);

    @Mapping(target = "patientMedicalHistory", source = "medicalHistory")
    @Mapping(target = "gender",        qualifiedByName = "toGender")
    @Mapping(target = "maritalStatus", qualifiedByName = "toMaritalStatus")
    com.pranicdoc.common.dto.Patient toPatientSnapshot(
            com.pranicdoc.cph.patient.model.Patient p);

    // ── Sub-mappers ───────────────────────────────────────────────────────────

    @Mapping(target = "healingCount",       nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
    @Mapping(target = "isHealingContinued", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
    com.pranicdoc.common.dto.CaseClosure toCaseClosure(
            com.pranicdoc.cph.healing.model.CaseClosure e);

    com.pranicdoc.common.dto.Patient.PatientMedicalHistory toPatientMedicalHistory(
            com.pranicdoc.cph.patient.model.PatientMedicalHistory h);

    HealingDetailsSnapshot toHealingDetailsSnapshot(HealingDetails d);

    @Mapping(target = "targets", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
    TreatmentStepSnapshot toTreatmentStepSnapshot(TreatmentStep t);

    MedicalSnapshot         toMedicalSnapshot(CaseMedicalSnapshot m);
    SessionSnapshot         toSessionSnapshot(HealingSession s);
    ChakraScanSnapshot      toChakraScanSnapshot(ChakraScan scan);
    ChakraReadingSnapshot   toChakraReadingSnapshot(ChakraReading r);
    SymptomSnapshot         toSymptomSnapshot(CaseSymptom s);
    MedicationSnapshot      toMedicationSnapshot(CaseMedication m);
    SymptomFeedbackSnapshot toSymptomFeedbackSnapshot(SymptomFeedback f);

    @Mapping(target = "feedbackType", expression = "java(f.getFeedbackType() != null ? f.getFeedbackType().name() : null)")
    QualitativeFeedbackSnapshot toQualitativeFeedbackSnapshot(QualitativeFeedback f);

    ProtocolEntrySnapshot toProtocolEntrySnapshot(ProtocolEntry e);

    @Mapping(target = "usageType", expression = "java(c.getUsageType() != null ? c.getUsageType().name() : null)")
    ProtocolColorSnapshot toProtocolColorSnapshot(ProtocolColor c);

    // ── Default methods for custom logic ──────────────────────────────────────

    @Named("toSortedSessions")
    default List<SessionSnapshot> toSortedSessions(List<HealingSession> sessions) {
        if (sessions == null) return Collections.emptyList();
        return sessions.stream()
                .sorted(Comparator.comparingInt(HealingSession::getSessionNumber))
                .map(this::toSessionSnapshot)
                .toList();
    }

    @Named("toGender")
    default com.pranicdoc.common.dto.enums.Gender toGender(
            com.pranicdoc.cph.common.enums.Gender g) {
        return g != null ? com.pranicdoc.common.dto.enums.Gender.valueOf(g.name()) : null;
    }

    @Named("toMaritalStatus")
    default com.pranicdoc.common.dto.enums.MaritalStatus toMaritalStatus(
            com.pranicdoc.cph.common.enums.MaritalStatus ms) {
        return ms != null
                ? com.pranicdoc.common.dto.enums.MaritalStatus.valueOf(ms.name())
                : null;
    }
}
