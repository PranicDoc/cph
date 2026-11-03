// hooks/useHealingSessionForm.ts (simplified – just returns form values, no submission logic)
import { useForm } from "@tanstack/react-form";
import { SymptomFeedbackRequest } from "../interfaces";
import { getCurrentDateAndTime } from "../lib/utils";

type HealingSessionFormValues = {
  sessionDate: string;
  startTime: string;
  endTime: string;
  symptomFeedbacks?: SymptomFeedbackRequest[];
  protocolName: string;
  referenceBook: string;
  detailedProtocol: string;
  feedbackType: "INTERIM" | "FINAL";
  feedbackText: string;
  patientRemarks: string;
};

// Symptoms will seed the per-symptom feedback once that step is back in the wizard.
/** `initialSessionDate`: a saved session's own date when correcting one (otherwise today). */
export function useHealingSessionForm(_symptoms: any[], initialSessionDate?: string) {
  const { currentDate, startTime, endTime } = getCurrentDateAndTime();

  const defaultValues: HealingSessionFormValues = {
    sessionDate: initialSessionDate ?? currentDate,
    startTime,
    endTime,
    protocolName: "",
    referenceBook: "",
    detailedProtocol: "",
    feedbackType: "INTERIM",
    feedbackText: "",
    patientRemarks: "",
  };

  const form = useForm({
    defaultValues,
  });

  return { form };
}
