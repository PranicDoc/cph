package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.cph.healing.dto.DeclarationRequest;
import com.pranicdoc.cph.healing.model.Declaration;
import com.pranicdoc.cph.healing.model.DeclarationDates;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = DeclarationDates.class)
public interface DeclarationMapper {
    @Mapping(target = "signedAt", expression = "java(DeclarationDates.parseSignedAt(declarationRequest.signedAt()))")
    Declaration toEntity(DeclarationRequest declarationRequest);
}
