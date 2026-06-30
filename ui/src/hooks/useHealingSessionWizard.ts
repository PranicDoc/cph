import { useCallback, useState } from "react";
import type { ChakraDocEntry } from "../components/ChakraScan";
import { useReferenceData } from "./useReferenceData";
import { useHealingSessionForm } from "./useHealingSessionForm";
import { constructHealingSessionPayload, extractDetail } from "../lib/utils";
import api from "../lib/axios";
import type { SymptomFeedbackRequest } from "../interfaces";
import { toast } from "sonner";

const FINAL_FORM_STEP = 2;
const COMPLETED_STEP = 3;

interface UseHealingSessionWizardParams {
  caseId: string;
  symptoms: any[];
}

function mapScanEntry(chakra: ChakraDocEntry) {
  return {
    chakraId: chakra.id,
    chakraName: chakra.name,
    energyLevel: chakra.energyLevel,
    activationLevel: chakra.activationLevel,
    notes: chakra.notes ?? "",
  };
}

export function useHealingSessionWizard({
  caseId,
  symptoms,
}: UseHealingSessionWizardParams) {
  const [step, setStep] = useState(1);
  const [preScanChakraData, setPreScanChakraData] = useState<ChakraDocEntry[]>(
    []
  );
  const [postScanChakraData, setPostScanChakraData] = useState<
    ChakraDocEntry[]
  >([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const referenceData = useReferenceData();
  const { form } = useHealingSessionForm(symptoms);

  const handleBack = useCallback(() => {
    setStep((current) => Math.max(1, current - 1));
  }, []);

  const handleNext = useCallback(async () => {
    await form.validateAllFields("change");
    const hasErrors = Object.values(form.state.fieldMeta).some(
      (meta: any) => meta.errors && meta.errors.length > 0
    );
    if (hasErrors) {
      return;
    }
    if (step < FINAL_FORM_STEP) {
      setStep(step + 1);
      return;
    }

    setIsSubmitting(true);

    try {
      const values = form.state.values;

      const hasProtocol = values.protocolName || values.referenceBook || values.detailedProtocol;
      const protocolEntries = hasProtocol
        ? [{ protocolName: values.protocolName, referenceBook: values.referenceBook, detailedProtocol: values.detailedProtocol, colors: [] }]
        : [];

      const payload = constructHealingSessionPayload({
        caseId,
        sessionDate: values.sessionDate,
        startTime: values.startTime,
        endTime: values.endTime,
        preScanData: preScanChakraData.map(mapScanEntry),
        postScanData: postScanChakraData.map(mapScanEntry),
        // symptomFeedbacks: values.symptomFeedbacks,
        protocolEntries,
        feedbackType: values.feedbackType,
        feedbackText: values.feedbackText,
        patientRemarks: values.patientRemarks,
      });

      await api.post("/api/v1/sessions/case", payload);
      setStep(COMPLETED_STEP);
    } catch (error) {
      toast.error(extractDetail(error) || "Failed to save session — please try again", { duration: 5000 });
    } finally {
      setIsSubmitting(false);
    }
  }, [caseId, form, postScanChakraData, preScanChakraData, step]);

  return {
    step,
    form,
    referenceData,
    preScanChakraData,
    postScanChakraData,
    isSubmitting,
    setPreScanChakraData,
    setPostScanChakraData,
    handleBack,
    handleNext,
  };
}
