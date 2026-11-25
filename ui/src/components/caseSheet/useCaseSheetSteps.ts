import { useState } from "react";
import type { ChiefComplaintRow } from "@/lib/chiefComplaints";
import { chiefComplaintsError } from "@/lib/chiefComplaints";

export const CASE_SHEET_STEPS = ["Chief complaints", "Presenting illness", "Lab findings and more"] as const;
export const LAST_STEP = CASE_SHEET_STEPS.length - 1;

/**
 * Which step of the case sheet is showing. The later steps open only once the chief complaints are complete;
 * trying to move on before that shows the table's message. Moving between steps saves nothing.
 */
export function useCaseSheetSteps(form: {
  state: { values: { chiefComplaints: ChiefComplaintRow[] } };
  validateField: (name: "chiefComplaints", cause: "submit") => unknown;
}) {
  const [step, setStep] = useState(0);

  const goTo = (target: number) => {
    if (target > 0 && chiefComplaintsError(form.state.values.chiefComplaints)) {
      form.validateField("chiefComplaints", "submit");
      setStep(0);
      return;
    }
    setStep(Math.max(0, Math.min(target, LAST_STEP)));
  };

  return { step, goTo, isLast: step === LAST_STEP };
}
