package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.cph.healing.dto.CaseUpdateRequest;
import com.pranicdoc.cph.healing.dto.SymptomRequest;
import com.pranicdoc.cph.healing.model.CaseSymptom;
import com.pranicdoc.cph.healing.model.HealingCase;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HealingCaseMapperTest {

    private final HealingCaseMapper mapper = Mappers.getMapper(HealingCaseMapper.class);

    @Test
    void updateMappingLeavesSymptomsToTheSyncSoNoUnlinkedRowIsInserted() {
        HealingCase entity = HealingCase.builder().build();
        CaseSymptom existing = CaseSymptom.builder().symptomName("Headache").healingCase(entity).build();
        existing.setId(UUID.randomUUID());
        entity.getSymptoms().add(existing);

        CaseUpdateRequest request = new CaseUpdateRequest(entity.getId(), "cond",
                List.of(new SymptomRequest("Headache", "", 1)),
                null, null, null, null, null, null, null, false, null, false, null, null, null);

        mapper.toEntity(request, entity);

        assertThat(entity.getSymptoms()).containsExactly(existing);
        assertThat(entity.getSymptoms().get(0).getHealingCase()).isSameAs(entity);
    }
}
