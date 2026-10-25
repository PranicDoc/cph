import { severityToRating } from "@/ui-new/components/SeveritySlider";

/**
 * The 0-10 rating the backend stores for a value picked on the feedback form's symptom scale:
 * the new view's severity slider, or the classic view's five-step emoji scale (x2).
 */
export function toSymptomRating(value: number, isNewView: boolean): number {
  return isNewView ? severityToRating(value) : value * 2;
}
