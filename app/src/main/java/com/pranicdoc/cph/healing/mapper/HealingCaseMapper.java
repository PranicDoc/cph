package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.cph.healing.dto.*;
import com.pranicdoc.cph.healing.model.CaseMedication;
import com.pranicdoc.cph.healing.model.CaseSymptom;
import com.pranicdoc.cph.healing.model.Declaration;
import com.pranicdoc.cph.healing.model.HealingCase;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

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
    HealingCase toEntity(CaseCreateRequest request);

    Declaration toDeclaration(DeclarationRequest request);

    void toEntity(CaseUpdateRequest updateRequest, @MappingTarget HealingCase entity);

    @Mapping(target = "addedDate", expression = "java(java.time.LocalDate.now())")
    @Mapping(target = "healingCase", ignore = true)
    CaseSymptom toSymptom(SymptomRequest symptomRequest);

    @Mapping(target = "healingCase", ignore = true)
    CaseMedication toMedication(MedicationRequest medicationRequest);
}
