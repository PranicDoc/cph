import { serializeExercise } from "./exercise";

/** A text value as the medical history stores it: trimmed, or null when empty. */
const orNull = (v: unknown) => (typeof v === "string" && v.trim() ? v.trim() : null);

/** A measurement as the API takes it: a number, or null when the box was left empty. */
export const numberOrNull = (v: unknown): number | null => {
  if (v === null || v === undefined || String(v).trim() === "") return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
};

/**
 * What PUT /api/v1/patients/{id}/medical-history gets. The whole form is sent (the server replaces the
 * history), exercise is stored as one string, and the cancer details are dropped when cancer is not ticked.
 */
export function buildMedicalHistoryUpdate(values: Record<string, unknown>) {
  const cancer = values.cancer === true;
  return {
    generalHealth: orNull(values.generalHealth),
    diet: orNull(values.diet),
    exercise: serializeExercise(Array.isArray(values.exercise) ? (values.exercise as string[]) : []),
    exerciseFrequency: orNull(values.exerciseFrequency),
    hypertension: values.hypertension === true,
    giIssues: values.giIssues === true,
    stressDepressionAnxiety: values.stressDepressionAnxiety === true,
    pregnant: values.pregnant === true,
    cancer,
    cancerLocation: cancer ? orNull(values.cancerLocation) : null,
    cancerDetails: cancer ? orNull(values.cancerDetails) : null,
    additionalNotes: orNull(values.additionalNotes),
    heightCm: numberOrNull(values.heightCm),
    weightKg: numberOrNull(values.weightKg),
    pastHistory: orNull(values.pastHistory),
    surgicalHistory: orNull(values.surgicalHistory),
    familyHistory: orNull(values.familyHistory),
    psychosocialHistory: orNull(values.psychosocialHistory),
  };
}
