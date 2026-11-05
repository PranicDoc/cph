/**
 * The most characters each kind of typed field may hold, sized so the text fits its box in the case
 * PDF. The server enforces the same numbers: keep app/.../common/validation/TextLimits.java in step.
 */
export const TEXT_LIMITS = {
  /** The server has long capped a patient's first and last name at 50. */
  firstNameLimit: 50,
  /** Names and short labels. */
  shortText: 100,
  symptomName: 60,
  colorCode: 30,
  /** Columns the database caps at 255. */
  note: 255,
  /** The small "if yes, explain here" boxes. */
  explain: 200,
  interimFeedback: 400,
  /** Medical condition and healing details boxes. */
  mediumBox: 500,
  /** The wide boxes on the medical history page. */
  historyBox: 600,
  finalFeedback: 1500,
  closureComments: 3000,
} as const;
