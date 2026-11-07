package com.pranicdoc.cph.healing.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.healing.dto.UsualPicksResponse;
import com.pranicdoc.cph.healing.dto.UsualPicksResponse.Pick;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository.ColorsOnly;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Unit coverage for {@link HealerProtocolUnderstandingService#getUsualPicks}. */
@ExtendWith(MockitoExtension.class)
class HealerProtocolUnderstandingUsualPicksTest {

    @Mock private CaseRepository caseRepository;
    @Mock private HealerProtocolUnderstandingRepository hpuRepository;

    @Mock private SecurityContextUtil securityContextUtil;

    private HealerProtocolUnderstandingService service;
    private final UUID caseId = UUID.randomUUID();
    private final UUID healerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new HealerProtocolUnderstandingService(caseRepository, hpuRepository, null, null, null, null,
                null, null, new ObjectMapper(), securityContextUtil);
        HealingCase healingCase = HealingCase.builder().build();
        healingCase.setId(caseId);
        healingCase.setHealerId(healerId);
        lenient().when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        lenient().when(caseRepository.findById(caseId)).thenReturn(Optional.of(healingCase));
    }

    private static ColorsOnly row(UUID forCase, String cleansing, String energising) {
        return new ColorsOnly(forCase, cleansing, energising);
    }

    private void rows(ColorsOnly... rows) {
        when(hpuRepository.findColorsByHealerIdExcludingCase(eq(healerId), eq(caseId), any()))
                .thenReturn(List.of(rows));
    }

    /** JSON map for one chakra from alternating colour name / properties arguments. */
    private static String colors(String chakra, String... nameAndProperty) {
        StringBuilder sb = new StringBuilder("{\"" + chakra + "\":[");
        for (int i = 0; i < nameAndProperty.length; i += 2) {
            if (i > 0) sb.append(',');
            String prop = nameAndProperty[i + 1] == null ? "" : nameAndProperty[i + 1];
            sb.append("{\"colorCode\":\"#000\",\"colorName\":\"").append(nameAndProperty[i])
                    .append("\",\"properties\":\"").append(prop).append("\"}");
        }
        return sb.append("]}").toString();
    }

    @Test
    void ranksByCountThenName_limitsToFour_andSplitsOverallFromPerChakra() {
        rows(
                row(UUID.randomUUID(), colors("Heart", "Green", "Calm", "Green", "Calm", "Blue", "Cool"), "{}"),
                row(UUID.randomUUID(), colors("Throat", "Green", "Calm", "Red", "Heat", "Amber", "A", "Violet", "V"), "{}"),
                row(UUID.randomUUID(), colors("Heart", "Zed", "Z"), "{}"));

        UsualPicksResponse result = service.getUsualPicks(caseId);

        assertThat(result.overall().cleansing()).containsExactly(
                new Pick("Green", "Calm"), new Pick("Amber", "A"), new Pick("Blue", "Cool"), new Pick("Red", "Heat"));
        assertThat(result.byChakra().keySet()).containsExactlyInAnyOrder("Heart", "Throat");
        assertThat(result.byChakra().get("Heart").cleansing()).containsExactly(
                new Pick("Green", "Calm"), new Pick("Blue", "Cool"), new Pick("Zed", "Z"));
        assertThat(result.overall().energising()).isEmpty();
    }

    @Test
    void usesFirstPropertyOrNull_skipsBlankColour_andKeepsCategoriesApart() {
        rows(row(UUID.randomUUID(), colors("Heart", "Green", "Calm, Cool", "Plain", null, "", "X"),
                colors("Heart", "Gold", "Boost")));

        UsualPicksResponse result = service.getUsualPicks(caseId);

        assertThat(result.overall().cleansing()).containsExactly(new Pick("Green", "Calm"), new Pick("Plain", null));
        assertThat(result.overall().energising()).containsExactly(new Pick("Gold", "Boost"));
    }

    @Test
    void noOtherCases_returnsEmptyLists() {
        rows();

        UsualPicksResponse result = service.getUsualPicks(caseId);

        assertThat(result.overall().cleansing()).isEmpty();
        assertThat(result.overall().energising()).isEmpty();
        assertThat(result.byChakra()).isEmpty();
    }

    @Test
    void unknownCase_isNotFound() {
        UUID other = UUID.randomUUID();
        when(caseRepository.findById(other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUsualPicks(other)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anotherHealersCase_isAccessDenied() {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.getUsualPicks(caseId)).isInstanceOf(AccessDeniedException.class);
    }
}
