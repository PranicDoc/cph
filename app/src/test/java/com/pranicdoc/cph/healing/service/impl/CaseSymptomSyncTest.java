package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.healing.dto.SymptomRequest;
import com.pranicdoc.cph.healing.model.CaseSymptom;
import com.pranicdoc.cph.healing.model.HealingCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaseSymptomSyncTest {

    private static CaseSymptom symptom(String name) {
        CaseSymptom s = CaseSymptom.builder().symptomName(name).build();
        s.setId(UUID.randomUUID());
        return s;
    }

    private static CaseSymptom fromRequest(SymptomRequest r) {
        return CaseSymptom.builder().symptomName(r.symptomName()).description(r.description()).severity(r.severity()).build();
    }

    private static HealingCase caseWith(CaseSymptom... symptoms) {
        HealingCase c = HealingCase.builder().build();
        c.getSymptoms().addAll(List.of(symptoms));
        return c;
    }

    @Test
    void symptomsThatStayKeepTheirIdSoFeedbackStaysAttached() {
        CaseSymptom headache = symptom("Headache");
        CaseSymptom fever = symptom("Fever");
        HealingCase c = caseWith(headache, fever);

        CaseSymptomSync.apply(c, List.of(
                new SymptomRequest("Headache", "behind the eyes", 6),
                new SymptomRequest(" fever ", null, null),
                new SymptomRequest("Cough", null, null)), CaseSymptomSyncTest::fromRequest);

        assertThat(c.getSymptoms()).hasSize(3);
        assertThat(c.getSymptoms()).contains(headache, fever);
        assertThat(headache.getDescription()).isEqualTo("behind the eyes");
        assertThat(headache.getSeverity()).isEqualTo(6);
        assertThat(c.getSymptoms()).anyMatch(s -> "Cough".equals(s.getSymptomName()) && s.getHealingCase() == c);
    }

    @Test
    void aSymptomLeftOutOfTheEditIsRemoved() {
        CaseSymptom headache = symptom("Headache");
        CaseSymptom fever = symptom("Fever");
        HealingCase c = caseWith(headache, fever);

        List<UUID> removed = CaseSymptomSync.apply(
                c, List.of(new SymptomRequest("Fever", null, null)), CaseSymptomSyncTest::fromRequest);

        assertThat(c.getSymptoms()).containsExactly(fever);
        assertThat(removed).containsExactly(headache.getId());
    }

    @Test
    void aRepeatedNameInTheRequestMatchesOnlyOneExistingRow() {
        CaseSymptom headache = symptom("Headache");
        HealingCase c = caseWith(headache);

        CaseSymptomSync.apply(c, List.of(
                new SymptomRequest("Headache", null, null),
                new SymptomRequest("Headache", null, null)), CaseSymptomSyncTest::fromRequest);

        assertThat(c.getSymptoms()).hasSize(2).contains(headache);
    }
}
