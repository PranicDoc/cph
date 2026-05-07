package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.cph.healing.dto.*;
import com.pranicdoc.cph.healing.model.*;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface HealingCaseMapper {

    @Mapping(target = "healerId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "medicalSnapshot", ignore = true)
    @Mapping(target = "symptoms", ignore = true)
    @Mapping(target = "medications", ignore = true)
    @Mapping(target = "closure", ignore = true)
    @Mapping(target = "sessions", ignore = true)
    @Mapping(target = "declaration", ignore = true) // handled separately
    @Mapping(target = "healingDetails", ignore = true)
    HealingCase toEntity(CaseCreateRequest request);

    Declaration toDeclaration(DeclarationRequest request);

    @Mapping(target = "healingDetails", ignore = true)
    void toEntity(CaseUpdateRequest updateRequest, @MappingTarget HealingCase entity);

    @Mapping(target = "addedDate", expression = "java(java.time.LocalDate.now())")
    @Mapping(target = "healingCase", ignore = true)
    CaseSymptom toSymptom(SymptomRequest symptomRequest);

    List<CaseSymptom> toSymptoms(List<SymptomRequest> requests);

    @Mapping(target = "healingCase", ignore = true)
    CaseMedication toMedication(MedicationRequest medicationRequest);

    List<CaseMedication> toMedications(List<MedicationRequest> requests);

    @Mapping(target = "healingCase", ignore = true)
    @Mapping(target = "treatmentSteps", source = "treatmentSteps")
    HealingDetails toHealingDetails(HealingDetailsRequest request);

    @AfterMapping
    default void linkHealingDetails(@MappingTarget HealingCase entity) {
        HealingDetails details = entity.getHealingDetails();
        if (details != null) {
            details.setHealingCase(entity);
            if (details.getTreatmentSteps() != null) {
                for (TreatmentStep step : details.getTreatmentSteps()) {
                    step.setHealingDetails(details);
                }
            }
        }
    }

    @Mapping(target = "treatmentSteps", ignore = true)
    void updateHealingDetails(HealingDetailsRequest healingDetailsRequest, @MappingTarget HealingDetails existingDetails);
    TreatmentStep toTreatmentStep(TreatmentStepRequest stepReq);
}
