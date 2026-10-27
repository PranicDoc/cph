package com.pranicdoc.cph.healing.mapper;

import com.pranicdoc.common.dto.QualitativeFeedbackSnapshot;
import com.pranicdoc.common.dto.SymptomFeedbackSnapshot;
import com.pranicdoc.cph.healing.model.CaseClosure;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.model.HealingSession;
import com.pranicdoc.cph.healing.model.QualitativeFeedback;
import com.pranicdoc.cph.healing.model.SymptomFeedback;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CphCaseMapperFeedbackTest {

    private final CphCaseMapper mapper = new CphCaseMapperImpl();

    private static HealingCase caseWith(CaseClosure closure, QualitativeFeedback q, SymptomFeedback s) {
        HealingSession session = new HealingSession();
        session.setQualitativeFeedbacks(new java.util.ArrayList<>(q == null ? List.of() : List.of(q)));
        session.setSymptomFeedbacks(new java.util.ArrayList<>(s == null ? List.of() : List.of(s)));
        HealingCase healingCase = HealingCase.builder().build();
        healingCase.setSessions(new java.util.ArrayList<>(List.of(session)));
        healingCase.setClosure(closure);
        closure.setHealingCase(healingCase);
        return healingCase;
    }

    @Test
    void feedbackSnapshotsCarryTheirOwnDateAndSource() {
        QualitativeFeedback q = new QualitativeFeedback();
        q.setHealingDate(LocalDate.of(2026, 10, 5));
        SymptomFeedback s = new SymptomFeedback();
        s.setHealingDate(LocalDate.of(2026, 10, 6));

        QualitativeFeedbackSnapshot qs = mapper.toQualitativeFeedbackSnapshot(q);
        SymptomFeedbackSnapshot ss = mapper.toSymptomFeedbackSnapshot(s);

        assertThat(qs.healingDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(qs.source()).isEqualTo(q.getSource().name());
        assertThat(ss.healingDate()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(ss.source()).isEqualTo(s.getSource().name());
    }

    @Test
    void closureLastFeedbackDateIsTheLatestFeedbackNotTheStaleStoredOne() {
        CaseClosure closure = new CaseClosure();
        closure.setLastFeedbackDate(null);
        QualitativeFeedback q = new QualitativeFeedback();
        q.setHealingDate(LocalDate.of(2026, 10, 5));
        SymptomFeedback s = new SymptomFeedback();
        s.setHealingDate(LocalDate.of(2026, 10, 9));
        caseWith(closure, q, s);

        assertThat(mapper.toCaseClosure(closure).lastFeedbackDate()).isEqualTo(LocalDate.of(2026, 10, 9));
    }

    @Test
    void closureKeepsItsStoredDateWhenNoFeedbackHasOne() {
        CaseClosure closure = new CaseClosure();
        closure.setLastFeedbackDate(LocalDate.of(2026, 9, 1));
        caseWith(closure, null, null);

        assertThat(mapper.toCaseClosure(closure).lastFeedbackDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }
}
