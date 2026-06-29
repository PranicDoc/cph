// components/HealingSessionWizard/WizardNavigation.tsx
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
      <button
        type="button"
        onClick={onBack}
        className="px-6 py-2.5 border-2 border-border text-text font-medium rounded-lg hover:bg-background transition-colors"
      >
        Previous
      </button>
      <button
        type="submit"
        disabled={!canSubmit || isSubmitting}
        onClick={onSubmit}
        className="px-6 py-2.5 bg-primary text-white font-medium rounded-lg hover:bg-primary-dark transition-colors disabled:opacity-50"
      >
        {isSubmitting ? "Saving..." : step === 2 ? "Submit" : "Next"}
      </button>
    </div>
  );
}
