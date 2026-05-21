import { useMemo } from "react";
import { useHealingSessionWizard } from "../hooks/useHealingSessionWizard";
import {
  buildBackendChakraCatalog,
  buildPranaColorCatalog,
} from "./ChakraScan";
import { CompletionScreen } from "./HealingSessionWizard/CompletionScreen";
import { StepPostScan } from "./HealingSessionWizard/StepPostScan";
import { StepPreScan } from "./HealingSessionWizard/StepPreScan";
import { StepProtocolEntries } from "./HealingSessionWizard/StepProtocolEntries";
import { StepSymptomsFeedback } from "./HealingSessionWizard/StepSymptomsFeedback";
import { WizardHeader } from "./HealingSessionWizard/WizardHeader";
import { WizardNavigation } from "./HealingSessionWizard/WizardNavigation";
import { WizardProgress } from "./HealingSessionWizard/WizardProgress";

interface HealingSessionWizardProps {
  caseId: string;
  sessionNumber: number;
  symptoms: any[];
}

export const HealingSessionWizard = ({
  caseId,
  symptoms = [],
}: HealingSessionWizardProps) => {
  const {
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
  } = useHealingSessionWizard({ caseId, symptoms });

  const { colors, chakras, loading } = referenceData;
  const availableChakras = useMemo(
    () => buildBackendChakraCatalog(chakras),
    [chakras]
  );
  const pranaColorCatalog = useMemo(
    () => buildPranaColorCatalog(colors),
    [colors]
  );

  if (loading) {
    return <div>Loading reference data...</div>;
  }

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <WizardHeader step={step} />
      <WizardProgress step={step} />

      <div className="bg-card border border-border rounded-xl p-6 shadow-sm">
        {step === 1 && (
          <StepPreScan
            value={preScanChakraData}
            onChange={setPreScanChakraData}
            availableChakras={availableChakras}
            pranaColorCatalog={pranaColorCatalog}
          />
        )}

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

        {step === 3 && (
          <StepPostScan
            value={postScanChakraData}
            onChange={setPostScanChakraData}
            availableChakras={availableChakras}
            pranaColorCatalog={pranaColorCatalog}
          />
        )}

        {step === 4 && (
          <StepSymptomsFeedback
            form={form}
            symptoms={symptoms}
            onAddSymptom={handleAddSymptomFeedback}
            onUpdateSymptom={handleUpdateSymptomFeedback}
            onRemoveSymptom={handleRemoveSymptomFeedback}
          />
        )}

        {step === 5 && <CompletionScreen />}

        {step <= 4 && (
          <WizardNavigation
            step={step}
            isSubmitting={isSubmitting}
            onBack={handleBack}
            onSubmit={handleNext}
            canSubmit={true}
          />
        )}
      </div>
    </div>
  );
};
