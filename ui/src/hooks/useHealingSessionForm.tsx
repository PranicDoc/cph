// hooks/useHealingSessionForm.ts (simplified – just returns form values, no submission logic)
import { useForm } from "@tanstack/react-form";
import { ProtocolEntryRequest, SymptomFeedbackRequest } from "../interfaces";
import { getCurrentDateAndTime } from "../lib/utils";

type HealingSessionFormValues = {
  sessionDate: string;
  startTime: string;
  endTime: string;
  symptomFeedbacks: SymptomFeedbackRequest[];
  protocolEntries: ProtocolEntryRequest[];
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
    protocolEntries: [],
    feedbackType: "INTERIM",
    feedbackText: "",
    patientRemarks: "",
  };

  const form = useForm({
    defaultValues,
  });

  return { form };
}
