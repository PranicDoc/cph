import { useMemo, useEffect } from "react";
import { useNavigate } from "react-router-dom";
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
  symptoms: any[];
  onClose: () => void;
}

export const HealingSessionWizard = ({
  caseId,
  symptoms = [],
  onClose,
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
    handleBack,
    handleNext,
  } = useHealingSessionWizard({ caseId, symptoms });

  const navigate = useNavigate();

  useEffect(() => {
    if (step !== 3) return;
    const timer = setTimeout(() => navigate('/patients'), 2000);
    return () => clearTimeout(timer);
  }, [step, navigate]);

  const { colors, chakras, loading } = referenceData;
  const availableChakras = useMemo(
    () => buildBackendChakraCatalog(chakras),
    [chakras]
  );
  const pranaColorCatalog = useMemo(
    () => buildPranaColorCatalog(colors),
    [colors]
  );

  const handleWizardBack = () => {
    if (step === 1) {
      onClose();
    } else {
      handleBack();
    }
  };

  if (loading) {
    return <div>Loading reference data...</div>;
  }

  return (
    <div className="mx-auto space-y-6">
      <WizardHeader step={step} totalSteps={2} />
      <WizardProgress step={step} totalSteps={2} />

      <div className="bg-card border border-border rounded-xl p-6 shadow-sm">
        {step === 1 && (
          <StepPreScan
            value={preScanChakraData}
            onChange={setPreScanChakraData}
            availableChakras={availableChakras}
            pranaColorCatalog={pranaColorCatalog}
          />
        )}

        {/* {step === 2 && <StepProtocolEntries form={form} />} */}

        {step === 2 && (
          <StepPostScan
            value={postScanChakraData}
            onChange={setPostScanChakraData}
            availableChakras={availableChakras}
            pranaColorCatalog={pranaColorCatalog}
          />
        )}

        {/* {step === 4 && (
          <StepSymptomsFeedback
            form={form}
            symptoms={symptoms}
            onAddSymptom={handleAddSymptomFeedback}
            onUpdateSymptom={handleUpdateSymptomFeedback}
            onRemoveSymptom={handleRemoveSymptomFeedback}
          />
        )} */}

        {step === 3 && <CompletionScreen />}

        {step <= 2 && (
          <WizardNavigation
            step={step}
            isSubmitting={isSubmitting}
            onBack={handleWizardBack}
            onSubmit={handleNext}
            canSubmit={true}
          />
        )}
      </div>
    </div>
  );
};
