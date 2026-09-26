import { useMemo, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useHealingSessionWizard } from "../hooks/useHealingSessionWizard";
import { useChakrasForProtocols } from "../hooks/admin/useMasterData";
import { resolvePrefillChakras } from "../lib/resolveScanChakras";
import {
  buildBackendChakraCatalog,
  buildPranaColorCatalog,
} from "./ChakraScan";
import type { ChakraCatalogEntry } from "./ChakraScan/types/spatial-chakra";
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
  /** Protocol ids selected for this session — used only to PRE-POPULATE the scan rows with the
   * chakras those protocols are linked to (protocol_chakra). It never restricts the picker: the
   * healer can always choose from the full chakra catalog, so a protocol with no linked chakra
   * data still leaves every chakra available to add manually. */
  protocolIds?: string[];
}

const NO_CHAKRAS: ChakraCatalogEntry[] = [];

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
  // The full catalog: what the chakra picker (and voice parsing) can choose from.
  const availableChakras = useMemo(() => buildBackendChakraCatalog(chakras), [chakras]);
  // While protocolIds is non-empty but the lookup hasn't resolved yet, protocolChakras is
  // still [] — wait for it rather than render the wizard and then append prefilled rows a
  // moment later.
  const chakraFilterPending = protocolIds.length > 0 && protocolChakrasLoading;
  // The subset linked to the selected protocols: only pre-populated as rows, editable after.
  const prefillChakras = useMemo(
    () => (chakraFilterPending ? [] : resolvePrefillChakras(availableChakras, protocolChakras, protocolIds)),
    [availableChakras, protocolChakras, protocolIds, chakraFilterPending]
  );

  // Prefill each scan step once per wizard visit. The steps unmount/remount when the healer
  // goes Back/Next, and re-seeding on every mount would bring back chakras they deliberately
  // deleted from that scan.
  const prefilledSteps = useRef<Set<number>>(new Set());
  const contentReady = !loading && !chakraFilterPending;
  useEffect(() => {
    if (contentReady && step <= 2) prefilledSteps.current.add(step);
  }, [contentReady, step]);
  const stepPrefillChakras = prefilledSteps.current.has(step) ? NO_CHAKRAS : prefillChakras;

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
            prefillChakras={stepPrefillChakras}
          />
        )}

        {step === 2 && (
          <StepPostScan
            value={postScanChakraData}
            onChange={setPostScanChakraData}
            availableChakras={availableChakras}
            prefillChakras={stepPrefillChakras}
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
