// components/HealingSessionWizard/WizardNavigation.tsx
import { Button } from "@/components/ui/button";

interface WizardNavigationProps {
  step: number;
  isSubmitting: boolean;
  onBack: () => void;
  onSubmit: () => void;
  canSubmit: boolean;
}

export function WizardNavigation({
  step,
  isSubmitting,
  onBack,
  onSubmit,
  canSubmit,
}: WizardNavigationProps) {
  return (
    <div className="flex justify-between pt-6 border-t border-border mt-8">
      <Button
        type="button"
        variant="outline"
        onClick={onBack}
        className="px-6 py-2.5 h-auto border-2 text-text"
      >
        Previous
      </Button>
      <Button
        type="submit"
        disabled={!canSubmit || isSubmitting}
        onClick={onSubmit}
        className="px-6 py-2.5 h-auto"
      >
        {isSubmitting ? "Saving..." : step === 2 ? "Submit" : "Next"}
      </Button>
    </div>
  );
}
