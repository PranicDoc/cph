import { useUiVersion } from "../../ui-version";

interface WizardHeaderProps {
  step: number;
  totalSteps?: number;
}

export function WizardHeader({ step, totalSteps = 4 }: WizardHeaderProps) {
  const { isNewView } = useUiVersion();
  const stepLabel = step <= totalSteps ? `Step ${step} of ${totalSteps}` : "Completed";

  if (isNewView) {
    return (
      <div className="hlx" style={{ gap: 0 }}>
        <div className="surface" style={{ padding: "16px 20px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", marginBottom: 10 }}>
            <strong>{stepLabel}</strong>
            <span className="small muted">Healing session</span>
          </div>
          <div style={{ display: "flex", gap: 8 }} role="presentation">
            {Array.from({ length: totalSteps }, (_, i) => (
              <div key={i} style={{ height: 6, flex: 1, borderRadius: 99, background: step >= i + 1 ? "var(--primary)" : "var(--hair-strong)" }} />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-primary text-white p-4 rounded-xl flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
      <h1 className="text-xl font-bold">Healing Session</h1>

      <span className="text-sm font-medium bg-white/20 px-3 py-1 rounded-lg">
        {stepLabel}
      </span>
    </div>
  );
}
