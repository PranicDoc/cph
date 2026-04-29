package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.cph.healing.dto.DeclarationRequest;
import com.pranicdoc.cph.healing.model.Declaration;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DeclarationMapper {
    Declaration toEntity(DeclarationRequest declarationRequest);
}
