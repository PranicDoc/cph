package com.pranicdoc.cph.common.validation;

/**
 * The most characters each kind of typed field may hold, sized so the text fits its box in the case
 * PDF. The UI enforces the same numbers: keep ui/src/lib/textLimits.ts in step with this file.
 */
public final class TextLimits {

    /** Names and short labels. */
    public static final int SHORT_TEXT = 100;
    public static final int SYMPTOM_NAME = 60;
    public static final int COLOR_CODE = 30;
    /** Columns the database caps at 255. */
    public static final int NOTE = 255;
    /** The small "if yes, explain here" boxes. */
    public static final int EXPLAIN = 200;
    public static final int INTERIM_FEEDBACK = 400;
    /** Medical condition and healing details boxes. */
    public static final int MEDIUM_BOX = 500;
    /** The wide boxes on the medical history page. */
    public static final int HISTORY_BOX = 600;
    /** The optional reason for healing a chakra. */
    public static final int CHAKRA_REASON = 300;
    public static final int FINAL_FEEDBACK = 1500;
    public static final int CLOSURE_COMMENTS = 3000;

    private TextLimits() {}
}
