// components/HealingSessionWizard/StepSymptomsFeedback.tsx
import { DynamicFormEngine } from "../DynamicFormEngine";
import { qualitativeFeedbackSchema } from "../../pages/schemas/HealingSessionSchemas";

interface StepSymptomsFeedbackProps {
  form: any; // Use proper type from tanstack form
  symptoms: any[];
  onAddSymptom: () => void;
  onUpdateSymptom: (index: number, field: string, value: any) => void;
  onRemoveSymptom: (index: number) => void;
}

export function StepSymptomsFeedback({
  form,
  symptoms,
  onAddSymptom,
  onUpdateSymptom,
  onRemoveSymptom,
}: StepSymptomsFeedbackProps) {
  const symptomFeedbacks = form.state.values.symptomFeedbacks;

  return (
    <div className="space-y-8">
      <div className="space-y-4">
        <div className="flex justify-between items-center border-b pb-2">
          <h2 className="text-lg font-semibold">Symptom Feedback</h2>
          <button
            type="button"
            onClick={onAddSymptom}
            className="text-xs text-primary font-medium px-3 py-1.5 hover:bg-primary/10 rounded-lg transition-colors"
          >
            + Add Feedback Row
          </button>
        </div>
        <div className="space-y-4">
          {symptomFeedbacks.length === 0 && (
            <p className="text-sm text-text-light italic">
              No symptoms to review.
            </p>
          )}
          {symptomFeedbacks.map((feedback: any, idx: number) => (
            <div
              key={idx}
              className="bg-background p-4 rounded-lg border border-border space-y-3"
            >
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <select
                  value={feedback.caseSymptomId}
                  onChange={(e) =>
                    onUpdateSymptom(idx, "caseSymptomId", e.target.value)
                  }
                  className="px-3 py-2 bg-white border border-border rounded text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="">Select symptom...</option>
                  {symptoms.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.symptomName || s.name}
                    </option>
                  ))}
                </select>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-text-light w-12">Rating:</span>
                  <input
                    type="range"
                    min="0"
                    max="10"
                    value={feedback.rating}
                    onChange={(e) =>
                      onUpdateSymptom(idx, "rating", parseInt(e.target.value))
                    }
                    className="flex-1"
                  />
                  <span className="text-sm font-semibold w-8 text-center bg-muted py-1 rounded">
                    {feedback.rating}
                  </span>
                </div>
              </div>
              <input
                type="text"
                placeholder="Notes (optional)"
                value={feedback.notes || ""}
                onChange={(e) => onUpdateSymptom(idx, "notes", e.target.value)}
                className="w-full px-3 py-2 bg-white border border-border rounded text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
              <button
                type="button"
                onClick={() => onRemoveSymptom(idx)}
                className="text-xs text-red-600 font-medium hover:underline"
              >
                Remove
              </button>
            </div>
          ))}
        </div>
      </div>
      <div className="pt-2">
        <DynamicFormEngine form={form} schema={qualitativeFeedbackSchema} />
      </div>
    </div>
  );
}
