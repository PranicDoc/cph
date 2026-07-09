// components/HealingSessionWizard/StepSymptomsFeedback.tsx
import { DynamicFormEngine } from "../DynamicFormEngine";
import { qualitativeFeedbackSchema } from "../../pages/schemas/HealingSessionSchemas";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Slider } from "@/components/ui/slider";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

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
          <Button
            type="button"
            variant="ghost"
            size="xs"
            onClick={onAddSymptom}
            className="h-auto px-3 py-1.5 text-xs text-primary font-medium hover:bg-primary/10 hover:text-primary"
          >
            + Add Feedback Row
          </Button>
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
                <Select
                  value={feedback.caseSymptomId || ""}
                  onValueChange={(v) => onUpdateSymptom(idx, "caseSymptomId", v)}
                >
                  <SelectTrigger className="w-full border px-3 py-2 rounded-md">
                    <SelectValue placeholder="Select symptom..." />
                  </SelectTrigger>
                  <SelectContent>
                    {symptoms.map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.symptomName || s.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-text-light w-12">Rating:</span>
                  <Slider
                    min={0}
                    max={10}
                    step={1}
                    value={[feedback.rating]}
                    onValueChange={([v]) => onUpdateSymptom(idx, "rating", v)}
                    className="flex-1"
                  />
                  <span className="text-sm font-semibold w-8 text-center bg-muted py-1 rounded">
                    {feedback.rating}
                  </span>
                </div>
              </div>
              <Input
                type="text"
                placeholder="Notes (optional)"
                value={feedback.notes || ""}
                onChange={(e) => onUpdateSymptom(idx, "notes", e.target.value)}
                className="border px-3 py-2 rounded-md"
              />
              <Button
                type="button"
                variant="link"
                onClick={() => onRemoveSymptom(idx)}
                className="h-auto p-0 text-xs font-medium text-red-600 hover:text-red-600"
              >
                Remove
              </Button>
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
