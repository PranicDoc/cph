package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.cph.healing.model.Declaration;
import com.pranicdoc.cph.healing.model.HealingCase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CphCaseMapperDeclarationDateTest {

    /** The snapshot's other parts are out of scope here; only the date is under test. */
    private final CphCaseMapper mapper = new CphCaseMapperImpl();

    @Test
    void theDateIsTheSignedInstantInIndiaTime() {
        HealingCase healingCase = HealingCase.builder().build();
        healingCase.setDeclaration(Declaration.builder().signedAt(Instant.parse("2026-08-14T19:00:00Z")).build());

        assertThat(mapper.declarationDate(healingCase)).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    void noDeclarationOrNoStoredDateGivesNoDate() {
        HealingCase healingCase = HealingCase.builder().build();
        assertThat(mapper.declarationDate(healingCase)).isNull();

        healingCase.setDeclaration(Declaration.builder().build());
        assertThat(mapper.declarationDate(healingCase)).isNull();
    }
}
