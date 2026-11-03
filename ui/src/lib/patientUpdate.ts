import { serializeExercise } from "./exercise";

/**
 * What PUT /api/v1/patients/{id} gets: text fields are always sent (an empty one clears an optional
 * field), the date and the two choices only when set, since the server ignores a missing field.
 */
export function buildPatientUpdate(values: Record<string, unknown>) {
  const text = (v: unknown) => (typeof v === "string" ? v.trim() : "");
  const set = (v: unknown) => (typeof v === "string" && v ? v : undefined);
  return {
    firstName: text(values.firstName),
    lastName: text(values.lastName),
    dob: set(values.dob),
    gender: set(values.gender),
    maritalStatus: set(values.maritalStatus),
    email: text(values.email),
    contactWhatsapp: text(values.contact),
    occupation: text(values.occupation),
    addressLine1: text(values.addressLine1),
    addressLine2: text(values.addressLine2),
    city: text(values.city),
    state: text(values.state),
    zip: text(values.zip),
  };
}

/** A text value as the medical history stores it: trimmed, or null when empty. */
const orNull = (v: unknown) => (typeof v === "string" && v.trim() ? v.trim() : null);

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
  };
}
