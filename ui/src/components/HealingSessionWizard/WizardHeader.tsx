interface WizardHeaderProps {
  step: number;
  totalSteps?: number;
}

export function WizardHeader({ step, totalSteps = 4 }: WizardHeaderProps) {
  const stepLabel = step <= totalSteps ? `Step ${step} of ${totalSteps}` : "Completed";

  return (
    <div className="bg-primary text-white p-4 rounded-xl flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
      <h1 className="text-xl font-bold">Healing Session</h1>

      <span className="text-sm font-medium bg-white/20 px-3 py-1 rounded-lg">
        {stepLabel}
      </span>
    </div>
  );
}
