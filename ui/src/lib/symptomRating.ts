import { severityToRating } from "@/ui-new/components/SeveritySlider";

/**
 * The 0-10 rating the backend stores (higher is better, as the reports read it) for the
 * severity picked on the feedback form's symptom slider (0 none to 10 worst), in either view.
 */
export function toSymptomRating(severity: number): number {
  return severityToRating(severity);
}
