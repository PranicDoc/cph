import { createContext, useState } from "react";
import type { ChiefComplaintRow } from "@/lib/chiefComplaints";
import { chiefComplaintsError, needsPatternDescription } from "@/lib/chiefComplaints";

export const CASE_SHEET_STEPS = ["Chief complaints", "Presenting illness", "Lab findings and more"] as const;
export const LAST_STEP = CASE_SHEET_STEPS.length - 1;

/** How many moves were refused for a missing pattern description; each one makes the illness step point it out. */
export const PatternCheckContext = createContext(0);

/**
 * Which step of the case sheet is showing. The later steps open only once the chief complaints are complete,
 * and the last one only once every "Pattern" timing is described; trying to move on before that stays on the
 * step concerned and shows its message. Moving between steps saves nothing.
 */
export function useCaseSheetSteps(form: {
  state: { values: { chiefComplaints: ChiefComplaintRow[] } };
  validateField: (name: "chiefComplaints", cause: "submit") => unknown;
}) {
  const [step, setStep] = useState(0);
  const [patternCheck, setPatternCheck] = useState(0);

  const goTo = (target: number) => {
    if (target > 0 && chiefComplaintsError(form.state.values.chiefComplaints)) {
      form.validateField("chiefComplaints", "submit");
      setStep(0);
      return;
    }
    if (target > 1 && form.state.values.chiefComplaints.some(needsPatternDescription)) {
      setPatternCheck((n) => n + 1);
      setStep(1);
      return;
    }
    setStep(Math.max(0, Math.min(target, LAST_STEP)));
  };

  return { step, goTo, isLast: step === LAST_STEP, patternCheck };
}
