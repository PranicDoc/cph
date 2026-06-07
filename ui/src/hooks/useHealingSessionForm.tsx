// hooks/useHealingSessionForm.ts (simplified – just returns form values, no submission logic)
import { useForm } from "@tanstack/react-form";
import { SymptomFeedbackRequest } from "../interfaces";
import { getCurrentDateAndTime } from "../lib/utils";

type HealingSessionFormValues = {
  sessionDate: string;
  startTime: string;
  endTime: string;
  symptomFeedbacks: SymptomFeedbackRequest[];
  protocolName: string;
  referenceBook: string;
  detailedProtocol: string;
  feedbackType: "INTERIM" | "FINAL";
  feedbackText: string;
  patientRemarks: string;
};

export function useHealingSessionForm(symptoms: any[]) {
  const { currentDate, startTime, endTime } = getCurrentDateAndTime();

  const initialSymptomFeedbacks: SymptomFeedbackRequest[] = symptoms.map(
    (s) => ({
      caseSymptomId: s.id,
      rating: 5,
      notes: "",
    })
  );

  const defaultValues: HealingSessionFormValues = {
    sessionDate: currentDate,
    startTime,
    endTime,
    symptomFeedbacks: initialSymptomFeedbacks,
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
