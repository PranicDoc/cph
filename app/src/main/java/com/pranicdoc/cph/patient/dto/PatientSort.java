package com.pranicdoc.cph.patient.dto;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

/**
 * Sort options for the patients list. Paths reference the aliases of the
 * fetchPatientsWithLatestCaseStatus query (p = Patient, hc = latest HealingCase).
 */
public enum PatientSort {
    CASE_DATE_DESC,
    CASE_DATE_ASC,
    NAME_ASC,
    NAME_DESC;

    public static final PatientSort DEFAULT = CASE_DATE_DESC;

    public Sort toSort() {
        return switch (this) {
            case CASE_DATE_DESC -> JpaSort.unsafe(Sort.Direction.DESC, "hc.createdAt");
            case CASE_DATE_ASC -> JpaSort.unsafe(Sort.Direction.ASC, "hc.createdAt");
            case NAME_ASC -> JpaSort.unsafe(Sort.Direction.ASC, "p.firstName", "p.lastName");
            case NAME_DESC -> JpaSort.unsafe(Sort.Direction.DESC, "p.firstName", "p.lastName");
        };
    }
}
