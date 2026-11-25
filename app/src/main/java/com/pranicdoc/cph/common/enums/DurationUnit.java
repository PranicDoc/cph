package com.pranicdoc.cph.common.enums;

/** The period a chief-complaint duration is counted in. */
public enum DurationUnit {
    HOURS("hour"), DAYS("day"), MONTHS("month"), YEARS("year");

    private final String singular;

    DurationUnit(String singular) { this.singular = singular; }

    /** "1 day", "3 months": the way the duration reads on the PDF and in summaries. */
    public String format(int value) {
        return value + " " + singular + (value == 1 ? "" : "s");
    }
}
