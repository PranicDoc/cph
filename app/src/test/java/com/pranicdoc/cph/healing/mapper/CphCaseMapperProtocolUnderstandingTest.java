package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.common.dto.CaseSnapshot;
import com.pranicdoc.cph.healing.model.HealerProtocolUnderstanding;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository;
import com.pranicdoc.cph.healing.service.impl.HealerProtocolUnderstandingService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The protocol understanding is one per case: it goes on the case snapshot, not on every session. */
class CphCaseMapperProtocolUnderstandingTest {

    @Test
    void protocolUnderstandingIsOnTheCaseOnce_andSessionsKeepTheirSessionDate() {
        UUID caseId = UUID.randomUUID();
        HealerProtocolUnderstanding saved = new HealerProtocolUnderstanding();
        saved.setHealingCaseId(caseId);
        saved.setProtocolName("Fever");
        HealerProtocolUnderstandingRepository repo = mock(HealerProtocolUnderstandingRepository.class);
        when(repo.findByHealingCaseId(caseId)).thenReturn(Optional.of(saved));
        HealerProtocolUnderstandingService service = mock(HealerProtocolUnderstandingService.class);
        when(service.deserializeColors(any())).thenReturn(Collections.emptyMap());
        when(service.deserializeChakraReadings(any())).thenReturn(List.of());
        when(service.deserializeReasons(any())).thenReturn(Map.of("Crown", "Headache"));

        CphCaseMapperImpl mapper = new CphCaseMapperImpl();
        mapper.hpuRepository = repo;
        mapper.hpuService = service;

        HealingCase healingCase = HealingCase.builder().build();
        healingCase.setId(caseId);
        healingCase.setSessions(new ArrayList<>(List.of(session(1, caseId, healingCase), session(2, caseId, healingCase))));

        CaseSnapshot snapshot = mapper.toCaseSnapshot(healingCase);

        assertThat(snapshot.healerProtocolUnderstanding().protocolName()).isEqualTo("Fever");
        assertThat(snapshot.healerProtocolUnderstanding().chakraReasons()).containsEntry("Crown", "Headache");
        assertThat(snapshot.sessions()).hasSize(2);
        assertThat(snapshot.sessions()).allSatisfy(s -> assertThat(s.healerProtocolUnderstanding()).isNull());
        assertThat(snapshot.sessions().get(0).sessionDate()).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    private static HealingSession session(int number, UUID caseId, HealingCase healingCase) {
        HealingSession s = new HealingSession();
        s.setHealingCase(healingCase);
        s.setSessionNumber(number);
        s.setSessionDate(LocalDate.of(2026, 11, 2));
        return s;
    }
}
