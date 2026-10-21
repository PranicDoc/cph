import { useUiVersion } from "../../ui-version";

interface WizardProgressProps {
  step: number;
  totalSteps?: number;
}

export function WizardProgress({ step, totalSteps = 4 }: WizardProgressProps) {
  const { isNewView } = useUiVersion();
  // The new-view header already draws the progress bars.
  if (isNewView) return null;
  return (
    <div className="flex gap-2">
      {Array.from({ length: totalSteps }, (_, index) => {
        const stepNumber = index + 1;

        return (
          <div
            key={stepNumber}
            className={`h-1.5 flex-1 rounded-full ${
              step >= stepNumber ? "bg-primary" : "bg-border"
            }`}
          />
        );
      })}
    </div>
  );
}
