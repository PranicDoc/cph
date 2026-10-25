import { SeveritySlider } from "@/ui-new/components/SeveritySlider";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

const EMOJI_SCALE = [
  { value: 1, emoji: "😠", label: "Terrible" },
  { value: 2, emoji: "🙁", label: "Bad" },
  { value: 3, emoji: "😐", label: "Okay" },
  { value: 4, emoji: "🙂", label: "Good" },
  { value: 5, emoji: "😄", label: "Great" },
];

export interface FeedbackSymptom {
  caseSymptomId: string;
  name: string;
}

/**
 * The per-symptom rating section of the patient feedback form, shared by the patient's
 * WhatsApp form and the healer's manual entry so both collect feedback the same way:
 * a severity slider in the new view, the emoji scale in the classic one.
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
  onChange: (caseSymptomId: string, value: number) => void;
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
      {symptoms.map((symptom) => (
        <div key={symptom.caseSymptomId} className="border border-border rounded-xl p-4">
          <div className="mb-4">
            <h3 className="font-medium text-primary-dark">{symptom.name}</h3>
          </div>

          {isNewView ? (
            <SeveritySlider
              label={symptom.name}
              value={ratings[symptom.caseSymptomId]}
              onChange={(v) => onChange(symptom.caseSymptomId, v)}
            />
          ) : (
            <div className="flex flex-wrap justify-between gap-2">
              {EMOJI_SCALE.map((item) => (
                <Button
                  key={item.value}
                  type="button"
                  variant="ghost"
                  onClick={() => onChange(symptom.caseSymptomId, item.value)}
                  title={item.label}
                  aria-label={`${symptom.name}: ${item.label}`}
                  aria-pressed={ratings[symptom.caseSymptomId] === item.value}
                  className={cn(
                    "flex flex-col items-center gap-1 h-auto p-2 rounded-xl transition-all",
                    ratings[symptom.caseSymptomId] === item.value
                      ? "transform scale-110 bg-primary/10 ring-2 ring-primary hover:bg-primary/10"
                      : "hover:bg-primary/5 opacity-60 hover:opacity-100"
                  )}
                >
                  <span className="text-4xl">{item.emoji}</span>
                  <span className="text-xs text-text-light">{item.label}</span>
                </Button>
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
