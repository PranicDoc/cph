import { useState } from "react";
import { useReferenceData } from "../hooks/useReferenceData";
import { useHealingSessionForm } from "../hooks/useHealingSessionForm";
import { constructHealingSessionPayload } from "../lib/utils";

import type { ChakraDocEntry } from "./ChakraScan/types/spatial-chakra";

import { CompletionScreen } from "./HealingSessionWizard/CompletionScreen";
import { StepPostScan } from "./HealingSessionWizard/StepPostScan";
import { StepPreScan } from "./HealingSessionWizard/StepPreScan";
import { StepProtocolEntries } from "./HealingSessionWizard/StepProtocolEntries";
import { StepSymptomsFeedback } from "./HealingSessionWizard/StepSymptomsFeedback";
import { WizardNavigation } from "./HealingSessionWizard/WizardNavigation";

import api from "../lib/axios";

export const HealingSessionWizard = ({
  caseId,
  sessionNumber,
  symptoms = [],
}: {
  caseId: string;
  sessionNumber: number;
  symptoms: any[];
}) => {
  const [step, setStep] = useState(1);

  // ─────────────────────────────────────────────
  // Chakra Scan State
  // ─────────────────────────────────────────────

  const [preScanChakraData, setPreScanChakraData] = useState<ChakraDocEntry[]>(
    []
  );

  const [postScanChakraData, setPostScanChakraData] = useState<
    ChakraDocEntry[]
  >([]);

  // ─────────────────────────────────────────────
  // Reference Data
  // ─────────────────────────────────────────────

  const { colors, chakras, loading } = useReferenceData();

  // ─────────────────────────────────────────────
  // Form
  // ─────────────────────────────────────────────

  const { form } = useHealingSessionForm(symptoms);

  const [isSubmitting, setIsSubmitting] = useState(false);

  // ─────────────────────────────────────────────
  // Protocol Entries
  // ─────────────────────────────────────────────

  const handleAddProtocolEntry = () => {
    const current = form.state.values.protocolEntries || [];

    form.setFieldValue("protocolEntries", [
      ...current,
      {
        chakraId: "",
        targetArea: "",
        technique: "",
        notes: "",
        colors: [],
      },
    ]);
  };

  const handleUpdateProtocolEntry = (
    index: number,
    field: string,
    value: any
  ) => {
    const updated = [...(form.state.values.protocolEntries || [])];

    updated[index] = {
      ...updated[index],
      [field]: value,
    };

    form.setFieldValue("protocolEntries", updated);
  };

  const handleRemoveProtocolEntry = (index: number) => {
    const updated = (form.state.values.protocolEntries || []).filter(
      (_, i) => i !== index
    );

    form.setFieldValue("protocolEntries", updated);
  };

  // ─────────────────────────────────────────────
  // Protocol Colors
  // ─────────────────────────────────────────────

  const handleAddColor = (entryIndex: number) => {
    const updated = [...(form.state.values.protocolEntries || [])];

    updated[entryIndex].colors = [
      ...(updated[entryIndex].colors || []),
      {
        colorId: "",
        usageType: "CLEANSING",
      },
    ];

    form.setFieldValue("protocolEntries", updated);
  };

  const handleUpdateColor = (
    entryIndex: number,
    colorIndex: number,
    field: string,
    value: any
  ) => {
    const updated = [...(form.state.values.protocolEntries || [])];

    const colors = updated[entryIndex].colors || [];

    colors[colorIndex] = {
      ...colors[colorIndex],
      [field]: value,
    };

    updated[entryIndex].colors = colors;

    form.setFieldValue("protocolEntries", updated);
  };

  const handleRemoveColor = (entryIndex: number, colorIndex: number) => {
    const updated = [...(form.state.values.protocolEntries || [])];

    updated[entryIndex].colors = (updated[entryIndex].colors || []).filter(
      (_: any, i: number) => i !== colorIndex
    );

    form.setFieldValue("protocolEntries", updated);
  };

  // ─────────────────────────────────────────────
  // Symptom Feedback
  // ─────────────────────────────────────────────

  const handleAddSymptomFeedback = () => {
    const current = form.state.values.symptomFeedbacks || [];

    form.setFieldValue("symptomFeedbacks", [
      ...current,
      {
        caseSymptomId: "",
        rating: 5,
        notes: "",
      },
    ]);
  };

  const handleUpdateSymptomFeedback = (
    index: number,
    field: string,
    value: any
  ) => {
    const updated = [...(form.state.values.symptomFeedbacks || [])];

    updated[index] = {
      ...updated[index],
      [field]: value,
    };

    form.setFieldValue("symptomFeedbacks", updated);
  };

  const handleRemoveSymptomFeedback = (index: number) => {
    const updated = (form.state.values.symptomFeedbacks || []).filter(
      (_, i) => i !== index
    );

    form.setFieldValue("symptomFeedbacks", updated);
  };

  // ─────────────────────────────────────────────
  // Scan Mapper
  // ─────────────────────────────────────────────

  const mapScanEntry = (c: ChakraDocEntry) => ({
    chakraId: c.id,
    chakraName: c.name,
    energyLevel: c.energyLevel,
    activationLevel: c.activationLevel,
    notes: c.notes ?? "",
  });

  // ─────────────────────────────────────────────
  // Navigation
  // ─────────────────────────────────────────────

  const handleNext = async () => {
    if (step < 4) {
      setStep(step + 1);
      return;
    }

    setIsSubmitting(true);

    try {
      const values = form.state.values;

      const payload = constructHealingSessionPayload({
        caseId,
        sessionDate: values.sessionDate,
        startTime: values.startTime,
        endTime: values.endTime,
        preScanData: preScanChakraData.map(mapScanEntry),
        postScanData: postScanChakraData.map(mapScanEntry),
        symptomFeedbacks: values.symptomFeedbacks,
        protocolEntries: values.protocolEntries,
        feedbackType: values.feedbackType,
        feedbackText: values.feedbackText,
        patientRemarks: values.patientRemarks,
      });
      await api.post("/api/v1/sessions/case", payload);
      setStep(5);
    } catch (error) {
      console.error("Failed to submit healing session:", error);
      alert("Failed to submit. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  };

  // ─────────────────────────────────────────────
  // Loading
  // ─────────────────────────────────────────────

  if (loading) {
    return <div>Loading reference data...</div>;
  }

  // ─────────────────────────────────────────────
  // Render
  // ─────────────────────────────────────────────

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Header */}

      <div className="bg-primary text-white p-4 rounded-xl flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <h1 className="text-xl font-bold">Healing Session</h1>

        <span className="text-sm font-medium bg-white/20 px-3 py-1 rounded-lg">
          {step <= 4 ? `Step ${step} of 4` : "Completed"}
        </span>
      </div>

      {/* Progress */}

      <div className="flex gap-2">
        {[1, 2, 3, 4].map((i) => (
          <div
            key={i}
            className={`h-1.5 flex-1 rounded-full ${
              step >= i ? "bg-primary" : "bg-border"
            }`}
          />
        ))}
      </div>

      {/* Content */}

      <div className="bg-card border border-border rounded-xl p-6 shadow-sm">
        {/* Step 1 */}

        {step === 1 && (
          <StepPreScan
            value={preScanChakraData}
            onChange={setPreScanChakraData}
          />
        )}

        {/* Step 2 */}

        {step === 2 && (
          <StepProtocolEntries
            form={form}
            chakras={chakras}
            colors={colors}
            onAdd={handleAddProtocolEntry}
            onUpdate={handleUpdateProtocolEntry}
            onRemove={handleRemoveProtocolEntry}
            onAddColor={handleAddColor}
            onUpdateColor={handleUpdateColor}
            onRemoveColor={handleRemoveColor}
          />
        )}

        {/* Step 3 */}

        {step === 3 && (
          <StepPostScan
            value={postScanChakraData}
            onChange={setPostScanChakraData}
          />
        )}

        {/* Step 4 */}

        {step === 4 && (
          <StepSymptomsFeedback
            form={form}
            symptoms={symptoms}
            onAddSymptom={handleAddSymptomFeedback}
            onUpdateSymptom={handleUpdateSymptomFeedback}
            onRemoveSymptom={handleRemoveSymptomFeedback}
          />
        )}

        {/* Step 5 */}

        {step === 5 && <CompletionScreen />}

        {/* Navigation */}

        {step <= 4 && (
          <WizardNavigation
            step={step}
            isSubmitting={isSubmitting}
            onBack={() => setStep(step - 1)}
            onSubmit={handleNext}
            canSubmit={true}
          />
        )}
      </div>
    </div>
  );
};
