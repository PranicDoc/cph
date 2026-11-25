package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.common.enums.Timing;
import com.pranicdoc.cph.common.validation.TextLimits;
import com.pranicdoc.cph.healing.dto.ChiefComplaintRequest;
import com.pranicdoc.cph.healing.dto.ProblemHistoryRequest;
import com.pranicdoc.cph.healing.model.CaseChiefComplaint;
import com.pranicdoc.cph.healing.model.HealingCase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The chief-complaint rules, in one place: a blank problem is skipped, a problem needs a duration and a
 * period, timing PATTERN needs its description, at least one problem and at most
 * {@link TextLimits#MAX_CHIEF_COMPLAINTS}. Also brings a case's rows in line with an edited list, keeping the rows that stay (same id) instead of recreating them.
 */
final class ChiefComplaintRules {

    private ChiefComplaintRules() {}

    /** The rows that count, trimmed and in order; throws IllegalArgumentException when the list breaks a rule. */
    static List<ChiefComplaintRequest> validRows(List<ChiefComplaintRequest> requested) {
        List<ChiefComplaintRequest> rows = new ArrayList<>();
        if (requested != null) {
            for (ChiefComplaintRequest row : requested) {
                if (row == null || row.problem() == null || row.problem().isBlank()) continue;
                String problem = row.problem().trim();
                if (row.durationValue() == null || row.durationValue() < 1 || row.durationUnit() == null) {
                    throw new IllegalArgumentException("Enter how long you have had \"" + problem + "\"");
                }
                ProblemHistoryRequest history = row.problemHistory();
                if (history != null && history.timing() == Timing.PATTERN && blankToNull(history.timingPattern()) == null) {
                    throw new IllegalArgumentException("Describe the pattern for \"" + problem + "\"");
                }
                rows.add(new ChiefComplaintRequest(row.id(), problem, row.durationValue(), row.durationUnit(),
                        row.problemHistory()));
            }
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("Chief complaint is required");
        if (rows.size() > TextLimits.MAX_CHIEF_COMPLAINTS) {
            throw new IllegalArgumentException(
                    "A case can list at most " + TextLimits.MAX_CHIEF_COMPLAINTS + " chief complaints");
        }
        return rows;
    }

    /** Replace the case's rows with the valid ones: an id that matches an existing row updates it, others are added. */
    static void apply(HealingCase healingCase, List<ChiefComplaintRequest> rows) {
        List<CaseChiefComplaint> existing = healingCase.getChiefComplaints();
        List<CaseChiefComplaint> result = new ArrayList<>();
        int order = 0;
        for (ChiefComplaintRequest request : rows) {
            CaseChiefComplaint row = request.id() == null ? null : findById(existing, request.id());
            // A repeated id is a new row: two problems must never be written onto one.
            if (row == null || result.contains(row)) {
                row = new CaseChiefComplaint();
                row.setHealingCase(healingCase);
            }
            row.setSortOrder(order++);
            row.setProblem(request.problem());
            row.setDurationValue(request.durationValue());
            row.setDurationUnit(request.durationUnit());
            copyHistory(request.problemHistory(), row);
            result.add(row);
        }
        existing.removeIf(e -> !result.contains(e));
        for (CaseChiefComplaint row : result) {
            if (!existing.contains(row)) existing.add(row);
        }
        // The list in memory answers this request, so it must read in the order sent, not the order loaded.
        existing.sort(java.util.Comparator.comparingInt(CaseChiefComplaint::getSortOrder));
    }

    /** The text shown where the complaint is one block: one "problem since N unit" line per row. */
    static String summary(List<CaseChiefComplaint> rows) {
        return rows.stream().map(CaseChiefComplaint::line).collect(java.util.stream.Collectors.joining("\n"));
    }

    private static CaseChiefComplaint findById(List<CaseChiefComplaint> existing, UUID id) {
        return existing.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
    }

    private static void copyHistory(ProblemHistoryRequest h, CaseChiefComplaint row) {
        if (h == null) {
            row.setOnset(null);
            row.setLocation(null);
            row.setCharacterText(null);
            row.setAggravatingFactor(null);
            row.setRelievingFactor(null);
            row.setTiming(null);
            row.setTimingPattern(null);
            return;
        }
        row.setOnset(h.onset());
        row.setLocation(blankToNull(h.location()));
        row.setCharacterText(blankToNull(h.characterText()));
        row.setAggravatingFactor(blankToNull(h.aggravatingFactor()));
        row.setRelievingFactor(blankToNull(h.relievingFactor()));
        row.setTiming(h.timing());
        // The pattern description only belongs to timing PATTERN; a stale one is dropped when the timing changes.
        row.setTimingPattern(h.timing() == Timing.PATTERN ? blankToNull(h.timingPattern()) : null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
