import { SeveritySlider, severityBand } from "@/ui-new/components/SeveritySlider";
import { Skeleton } from "@/components/ui/skeleton";
import { Slider } from "@/components/ui/slider";

export interface FeedbackSymptom {
  caseSymptomId: string;
  name: string;
}

/**
 * The per-symptom rating section of the patient feedback form, shared by the patient's
 * WhatsApp form and the healer's manual entry so both collect feedback the same way: a
 * 0 (none) to 10 (worst) severity slider per symptom -- the new view's SeveritySlider, or the
 * classic view's Slider (the new view's slider is only styled inside the new view).
 */
export function SymptomRatingList({
  symptoms,
  ratings,
  onChange,
  isNewView,
  loading,
}: {
  symptoms: FeedbackSymptom[];
  ratings: Record<string, number>;
  onChange: (caseSymptomId: string, severity: number) => void;
  isNewView: boolean;
  loading?: boolean;
}) {
  if (loading) {
    return (
      <div className="space-y-4">
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-28 w-full rounded-xl" />
        ))}
      </div>
    );
  }

  if (symptoms.length === 0) {
    return <p className="text-sm text-text-light italic">No symptoms recorded for this case.</p>;
  }

  return (
    <div className="space-y-4">
      {symptoms.map((symptom) => {
        const severity = ratings[symptom.caseSymptomId];
        const rated = severity !== undefined;
        return (
          <div key={symptom.caseSymptomId} className="border border-border rounded-xl p-4">
            <div className="mb-4">
              <h3 className="font-medium text-primary-dark">{symptom.name}</h3>
            </div>

            {isNewView ? (
              <SeveritySlider
                label={symptom.name}
                value={severity}
                onChange={(v) => onChange(symptom.caseSymptomId, v)}
              />
            ) : (
              <div className="space-y-2">
                <div className="flex items-center gap-3">
                  <Slider
                    min={0}
                    max={10}
                    step={1}
                    value={[severity ?? 0]}
                    // Commit as well as change, so releasing on the starting 0 still records "0 · None".
                    onValueChange={([v]) => onChange(symptom.caseSymptomId, v)}
                    onValueCommit={([v]) => onChange(symptom.caseSymptomId, v)}
                    aria-label={`${symptom.name}: severity from 0 (none) to 10 (worst)`}
                    className="flex-1"
                  />
                  <span className="w-28 text-right text-sm font-semibold text-text">
                    {rated ? `${severity} · ${severityBand(severity).label}` : "Not rated yet"}
                  </span>
                </div>
                <div className="flex justify-between text-xs text-text-light">
                  <span>0 None</span>
                  <span>10 Worst</span>
                </div>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
