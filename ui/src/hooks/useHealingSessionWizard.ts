import { useCallback, useState } from "react";
import type { ChakraDocEntry } from "../components/ChakraScan";
import { useReferenceData } from "./useReferenceData";
import { useHealingSessionForm } from "./useHealingSessionForm";
import { constructHealingSessionPayload } from "../lib/utils";
import api from "../lib/axios";
import type {
  ProtocolEntryRequest,
  SymptomFeedbackRequest,
} from "../interfaces";

const FINAL_FORM_STEP = 4;
const COMPLETED_STEP = 5;

interface UseHealingSessionWizardParams {
  caseId: string;
  symptoms: any[];
}

function createProtocolEntry(): ProtocolEntryRequest {
  return {
    chakraId: "",
    targetArea: "",
    technique: "",
    notes: "",
    colors: [],
  };
}

function createSymptomFeedback(): SymptomFeedbackRequest {
  return {
    caseSymptomId: "",
    rating: 5,
    notes: "",
  };
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

function hasIncompleteProtocolColorRows(entries: ProtocolEntryRequest[]) {
  return entries.some((entry) =>
    (entry.colors || []).some((color) => !color.colorId)
  );
}

export function useHealingSessionWizard({
  caseId,
  symptoms,
}: UseHealingSessionWizardParams) {
  const [step, setStep] = useState(1);
  const [preScanChakraData, setPreScanChakraData] = useState<
    ChakraDocEntry[]
  >([]);
  const [postScanChakraData, setPostScanChakraData] = useState<
    ChakraDocEntry[]
  >([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const referenceData = useReferenceData();
  const { form } = useHealingSessionForm(symptoms);

  const handleAddProtocolEntry = useCallback(() => {
    const current = form.state.values.protocolEntries || [];
    form.setFieldValue("protocolEntries", [
      ...current,
      createProtocolEntry(),
    ]);
  }, [form]);

  const handleUpdateProtocolEntry = useCallback(
    (index: number, field: string, value: any) => {
      const updated = [...(form.state.values.protocolEntries || [])];

      updated[index] = {
        ...updated[index],
        [field]: value,
      };

      form.setFieldValue("protocolEntries", updated);
    },
    [form]
  );

  const handleRemoveProtocolEntry = useCallback(
    (index: number) => {
      const updated = (form.state.values.protocolEntries || []).filter(
        (_, i) => i !== index
      );

      form.setFieldValue("protocolEntries", updated);
    },
    [form]
  );

  const handleAddColor = useCallback(
    (entryIndex: number) => {
      const updated = [...(form.state.values.protocolEntries || [])];

      updated[entryIndex].colors = [
        ...(updated[entryIndex].colors || []),
        {
          colorId: "",
          usageType: "CLEANSING",
        },
      ];

      form.setFieldValue("protocolEntries", updated);
    },
    [form]
  );

  const handleUpdateColor = useCallback(
    (entryIndex: number, colorIndex: number, field: string, value: any) => {
      const updated = [...(form.state.values.protocolEntries || [])];
      const colors = updated[entryIndex].colors || [];

      colors[colorIndex] = {
        ...colors[colorIndex],
        [field]: value,
      };

      updated[entryIndex].colors = colors;
      form.setFieldValue("protocolEntries", updated);
    },
    [form]
  );

  const handleRemoveColor = useCallback(
    (entryIndex: number, colorIndex: number) => {
      const updated = [...(form.state.values.protocolEntries || [])];

      updated[entryIndex].colors = (
        updated[entryIndex].colors || []
      ).filter((_, i) => i !== colorIndex);

      form.setFieldValue("protocolEntries", updated);
    },
    [form]
  );

  const handleAddSymptomFeedback = useCallback(() => {
    const current = form.state.values.symptomFeedbacks || [];

    form.setFieldValue("symptomFeedbacks", [
      ...current,
      createSymptomFeedback(),
    ]);
  }, [form]);

  const handleUpdateSymptomFeedback = useCallback(
    (index: number, field: string, value: any) => {
      const updated = [...(form.state.values.symptomFeedbacks || [])];

      updated[index] = {
        ...updated[index],
        [field]: value,
      };

      form.setFieldValue("symptomFeedbacks", updated);
    },
    [form]
  );

  const handleRemoveSymptomFeedback = useCallback(
    (index: number) => {
      const updated = (form.state.values.symptomFeedbacks || []).filter(
        (_, i) => i !== index
      );

      form.setFieldValue("symptomFeedbacks", updated);
    },
    [form]
  );

  const handleBack = useCallback(() => {
    setStep((current) => Math.max(1, current - 1));
  }, []);

  const handleNext = useCallback(async () => {
    if (step < FINAL_FORM_STEP) {
      setStep(step + 1);
      return;
    }

    setIsSubmitting(true);

    try {
      const values = form.state.values;
      const protocolEntries = values.protocolEntries || [];

      if (hasIncompleteProtocolColorRows(protocolEntries)) {
        window.alert("Please select a color for every protocol color row.");
        return;
      }

      const payload = constructHealingSessionPayload({
        caseId,
        sessionDate: values.sessionDate,
        startTime: values.startTime,
        endTime: values.endTime,
        preScanData: preScanChakraData.map(mapScanEntry),
        postScanData: postScanChakraData.map(mapScanEntry),
        symptomFeedbacks: values.symptomFeedbacks,
        protocolEntries,
        feedbackType: values.feedbackType,
        feedbackText: values.feedbackText,
        patientRemarks: values.patientRemarks,
      });

      await api.post("/api/v1/sessions/case", payload);
      setStep(COMPLETED_STEP);
    } catch (error) {
      console.error("Failed to submit healing session:", error);
      window.alert("Failed to submit. Please try again.");
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
    handleAddProtocolEntry,
    handleUpdateProtocolEntry,
    handleRemoveProtocolEntry,
    handleAddColor,
    handleUpdateColor,
    handleRemoveColor,
    handleAddSymptomFeedback,
    handleUpdateSymptomFeedback,
    handleRemoveSymptomFeedback,
    handleBack,
    handleNext,
  };
}
