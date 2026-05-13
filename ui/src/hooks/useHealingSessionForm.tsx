// hooks/useHealingSessionForm.ts (simplified – just returns form values, no submission logic)
import { useForm } from "@tanstack/react-form";
import { ProtocolEntryRequest, SymptomFeedbackRequest } from "../interfaces";
import { getCurrentDateAndTime } from "../lib/utils";

export function useHealingSessionForm(symptoms: any[]) {
  const { currentDate, startTime, endTime } = getCurrentDateAndTime();

  const initialSymptomFeedbacks: SymptomFeedbackRequest[] = symptoms.map(
    (s) => ({
      caseSymptomId: s.id,
      rating: 5,
      notes: "",
    })
  );

  const form = useForm({
    defaultValues: {
      sessionDate: currentDate,
      startTime: startTime,
      endTime: endTime,
      symptomFeedbacks: initialSymptomFeedbacks,
      protocolEntries: [] as ProtocolEntryRequest[],
      feedbackType: "INTERIM",
      feedbackText: "",
      patientRemarks: "",
    },
  });

  return { form };
}
