package com.pranicdoc.cph.healing.service.impl;

import com.pranicdoc.cph.healing.dto.SymptomRequest;
import com.pranicdoc.cph.healing.model.CaseSymptom;
import com.pranicdoc.cph.healing.model.HealingCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Brings a case's symptoms in line with an edited list without recreating the ones that stay.
 * Feedback refers to a symptom by its id, so replacing every row on each edit would leave all
 * earlier feedback pointing at symptoms that no longer exist.
 */
final class CaseSymptomSync {

    private CaseSymptomSync() {}

    /**
     * A requested symptom with the same name (ignoring case and surrounding spaces) as an existing one
     * updates that row; a new name adds a row; an existing symptom missing from the request is removed.
     */
    static void apply(HealingCase healingCase, List<SymptomRequest> requested,
                      Function<SymptomRequest, CaseSymptom> newSymptom) {
        List<CaseSymptom> existing = healingCase.getSymptoms();
        List<CaseSymptom> kept = new ArrayList<>();
        Set<CaseSymptom> matched = new HashSet<>();
        for (SymptomRequest request : requested) {
            CaseSymptom current = existing.stream()
                    .filter(s -> !matched.contains(s) && sameName(s.getSymptomName(), request.symptomName()))
                    .findFirst()
                    .orElse(null);
            if (current != null) {
                matched.add(current);
                current.setSymptomName(request.symptomName().trim());
                current.setDescription(request.description());
                current.setSeverity(request.severity());
                kept.add(current);
            } else {
                CaseSymptom added = newSymptom.apply(request);
                added.setHealingCase(healingCase);
                kept.add(added);
            }
        }
        existing.removeIf(s -> !matched.contains(s));
        for (CaseSymptom symptom : kept) {
            if (!matched.contains(symptom)) existing.add(symptom);
        }
    }

    private static boolean sameName(String a, String b) {
        return a != null && b != null && a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }
}
