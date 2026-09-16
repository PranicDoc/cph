import { useMemo, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useHealingSessionWizard } from "../hooks/useHealingSessionWizard";
import { useChakrasForProtocols } from "../hooks/admin/useMasterData";
import { resolveScanChakras } from "../lib/resolveScanChakras";
import {
  buildBackendChakraCatalog,
  buildPranaColorCatalog,
} from "./ChakraScan";
import { CompletionScreen } from "./HealingSessionWizard/CompletionScreen";
import { StepPostScan } from "./HealingSessionWizard/StepPostScan";
import { StepPreScan } from "./HealingSessionWizard/StepPreScan";
import { WizardHeader } from "./HealingSessionWizard/WizardHeader";
import { WizardNavigation } from "./HealingSessionWizard/WizardNavigation";
import { WizardProgress } from "./HealingSessionWizard/WizardProgress";

interface HealingSessionWizardProps {
  caseId: string;
  symptoms: any[];
  onClose: () => void;
  /** Protocol ids selected for this session — narrows the chakra scan list to the chakras
   * those protocols address. When no protocol is selected yet, shows the full chakra catalog
   * (nothing to filter by yet). Once a protocol IS selected, the scan list is exactly that
   * protocol's linked chakras — empty if it has none — never a silent fallback to the full
   * catalog, so a protocol with no chakra data doesn't masquerade as "every chakra applies". */
  protocolIds?: string[];
}

export const HealingSessionWizard = ({
  caseId,
  symptoms = [],
  onClose,
  protocolIds = [],
}: HealingSessionWizardProps) => {
  const {
    step,
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

  const { chakras, loading } = referenceData;
  const {
    data: protocolChakras = [],
    isLoading: protocolChakrasLoading,
  } = useChakrasForProtocols(protocolIds);
  // While protocolIds is non-empty but the lookup hasn't resolved yet, protocolChakras is
  // still [] — treat that as "not decided yet" (full catalog), NOT as "no chakras found",
  // so the scan list doesn't render the full catalog then jump to the filtered one a moment
  // later (that render-order matters once this feeds auto-populated rows, not just a picker).
  const chakraFilterPending = protocolIds.length > 0 && protocolChakrasLoading;
  const scanChakras = useMemo(() => {
    if (chakraFilterPending) return [];
    return resolveScanChakras(chakras, protocolChakras, protocolIds);
  }, [chakras, protocolChakras, protocolIds, chakraFilterPending]);
  const availableChakras = useMemo(
    () => buildBackendChakraCatalog(scanChakras),
    [scanChakras]
  );

  const handleWizardBack = () => {
    if (step === 1) {
      onClose();
    } else {
      handleBack();
    }
  };

  if (loading || chakraFilterPending) {
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
          />
        )}

        {step === 2 && (
          <StepPostScan
            value={postScanChakraData}
            onChange={setPostScanChakraData}
            availableChakras={availableChakras}
          />
        )}

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
