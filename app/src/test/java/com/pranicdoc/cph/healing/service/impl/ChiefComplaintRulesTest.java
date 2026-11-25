package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.common.enums.DurationUnit;
import com.pranicdoc.cph.common.enums.Onset;
import com.pranicdoc.cph.common.enums.Timing;
import com.pranicdoc.cph.healing.dto.ChiefComplaintRequest;
import com.pranicdoc.cph.healing.dto.ProblemHistoryRequest;
import com.pranicdoc.cph.healing.model.CaseChiefComplaint;
import com.pranicdoc.cph.healing.model.HealingCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChiefComplaintRulesTest {

    private static ChiefComplaintRequest row(String problem, Integer value, DurationUnit unit) {
        return new ChiefComplaintRequest(null, problem, value, unit, null);
    }

    @Test
    void blankProblemRowsAreSkippedEvenWithADuration() {
        List<ChiefComplaintRequest> rows = ChiefComplaintRules.validRows(List.of(
                row("Headache", 3, DurationUnit.MONTHS), row("  ", 2, DurationUnit.DAYS), row(null, null, null)));
        assertEquals(1, rows.size());
        assertEquals("Headache", rows.get(0).problem());
    }

    @Test
    void aProblemWithoutDurationOrPeriodIsRejectedByName() {
        IllegalArgumentException noDuration = assertThrows(IllegalArgumentException.class,
                () -> ChiefComplaintRules.validRows(List.of(row("Knee pain", null, DurationUnit.DAYS))));
        assertTrue(noDuration.getMessage().contains("Knee pain"));
        assertThrows(IllegalArgumentException.class,
                () -> ChiefComplaintRules.validRows(List.of(row("Knee pain", 2, null))));
        assertThrows(IllegalArgumentException.class,
                () -> ChiefComplaintRules.validRows(List.of(row("Knee pain", 0, DurationUnit.DAYS))));
    }

    @Test
    void atLeastOneProblemIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> ChiefComplaintRules.validRows(null));
        assertThrows(IllegalArgumentException.class, () -> ChiefComplaintRules.validRows(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ChiefComplaintRules.validRows(List.of(row(" ", null, null))));
    }

    @Test
    void atMostTenProblems() {
        List<ChiefComplaintRequest> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) ten.add(row("Problem " + i, 1, DurationUnit.DAYS));
        assertEquals(10, ChiefComplaintRules.validRows(ten).size());

        ten.add(row("Problem 10", 1, DurationUnit.DAYS));
        assertThrows(IllegalArgumentException.class, () -> ChiefComplaintRules.validRows(ten));
    }

    @Test
    void blankRowsDoNotCountTowardsTheCap() {
        List<ChiefComplaintRequest> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) rows.add(row("Problem " + i, 1, DurationUnit.DAYS));
        rows.add(row("", null, null));
        assertEquals(10, ChiefComplaintRules.validRows(rows).size());
    }

    @Test
    void applyKeepsRowsThatStayUpdatesThemAndDropsTheRest() {
        HealingCase c = HealingCase.builder().build();
        CaseChiefComplaint keep = new CaseChiefComplaint();
        keep.setId(UUID.randomUUID());
        keep.setProblem("Headache");
        CaseChiefComplaint drop = new CaseChiefComplaint();
        drop.setId(UUID.randomUUID());
        drop.setProblem("Old");
        c.getChiefComplaints().add(keep);
        c.getChiefComplaints().add(drop);

        ChiefComplaintRules.apply(c, List.of(
                new ChiefComplaintRequest(keep.getId(), "Headache and nausea", 4, DurationUnit.MONTHS, null),
                row("Knee pain", 2, DurationUnit.DAYS)));

        assertEquals(2, c.getChiefComplaints().size());
        assertSame(keep, c.getChiefComplaints().get(0));
        assertEquals("Headache and nausea", keep.getProblem());
        assertEquals(0, keep.getSortOrder());
        assertEquals("Knee pain", c.getChiefComplaints().get(1).getProblem());
        assertEquals(1, c.getChiefComplaints().get(1).getSortOrder());
        assertFalse(c.getChiefComplaints().contains(drop));
    }

    @Test
    void presentingIllnessIsCopiedAndAStalePatternIsDroppedWhenTimingIsNotPattern() {
        HealingCase c = HealingCase.builder().build();
        var history = new ProblemHistoryRequest(Onset.GRADUAL, " Forehead ", "Dull", "Screens", "Rest",
                Timing.PATTERN, "after 3 pm");
        ChiefComplaintRules.apply(c, List.of(
                new ChiefComplaintRequest(null, "Headache", 3, DurationUnit.MONTHS, history)));
        CaseChiefComplaint row = c.getChiefComplaints().get(0);
        assertEquals(Onset.GRADUAL, row.getOnset());
        assertEquals("Forehead", row.getLocation());
        assertEquals(Timing.PATTERN, row.getTiming());
        assertEquals("after 3 pm", row.getTimingPattern());

        var constant = new ProblemHistoryRequest(Onset.GRADUAL, null, null, null, null, Timing.CONSTANT, "after 3 pm");
        ChiefComplaintRules.apply(c, List.of(
                new ChiefComplaintRequest(row.getId() == null ? null : row.getId(), "Headache", 3, DurationUnit.MONTHS, constant)));
        assertNull(c.getChiefComplaints().get(0).getTimingPattern());
    }

    @Test
    void summaryHasOneLinePerProblemAndALegacyRowIsJustTheProblem() {
        HealingCase c = HealingCase.builder().build();
        CaseChiefComplaint a = new CaseChiefComplaint();
        a.setProblem("Headache");
        a.setDurationValue(3);
        a.setDurationUnit(DurationUnit.MONTHS);
        CaseChiefComplaint b = new CaseChiefComplaint();
        b.setProblem("Knee pain");
        b.setDurationValue(1);
        b.setDurationUnit(DurationUnit.DAYS);
        CaseChiefComplaint legacy = new CaseChiefComplaint();
        legacy.setProblem("Back pain and acidity");
        c.getChiefComplaints().addAll(List.of(a, b, legacy));

        assertEquals("Headache since 3 months\nKnee pain since 1 day\nBack pain and acidity",
                ChiefComplaintRules.summary(c.getChiefComplaints()));
        assertTrue(legacy.isLegacy());
        assertFalse(a.isLegacy());
    }
    @Test
    void aRepeatedIdNeverMergesTwoProblemsIntoOne() {
        HealingCase c = HealingCase.builder().build();
        CaseChiefComplaint saved = new CaseChiefComplaint();
        saved.setId(UUID.randomUUID());
        c.getChiefComplaints().add(saved);

        ChiefComplaintRules.apply(c, List.of(
                new ChiefComplaintRequest(saved.getId(), "Headache", 3, DurationUnit.MONTHS, null),
                new ChiefComplaintRequest(saved.getId(), "Knee pain", 2, DurationUnit.DAYS, null)));

        assertEquals(List.of("Headache", "Knee pain"),
                c.getChiefComplaints().stream().map(CaseChiefComplaint::getProblem).toList());
    }

    @Test
    void theRowsReadBackInTheOrderTheyWereSent() {
        HealingCase c = HealingCase.builder().build();
        CaseChiefComplaint saved = new CaseChiefComplaint();
        saved.setId(UUID.randomUUID());
        c.getChiefComplaints().add(saved);

        ChiefComplaintRules.apply(c, List.of(
                row("Knee pain", 2, DurationUnit.DAYS),
                new ChiefComplaintRequest(saved.getId(), "Headache", 3, DurationUnit.MONTHS, null)));

        assertEquals("Knee pain since 2 days\nHeadache since 3 months",
                ChiefComplaintRules.summary(c.getChiefComplaints()));
    }
}
