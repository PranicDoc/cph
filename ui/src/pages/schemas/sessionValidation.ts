/**
 * Mandatory-field rules for step 0 of the session form (the "Patient Medical
 * Details" screen). Shared by:
 *   - sessionSchema.ts field validators (per-field error display on submit)
 *   - Session.tsx "Start Session" button gate (greyed until fillable)
 *   - Session.tsx submit handler (block advancing to the wizard when invalid)
 *
 * A case saved with no chief complaint / no protocol renders as a blank View
 * page later, so both are required before a session can start.
 */

export type SessionStep0Values = {
  medicalCondition?: string | null;
  healingDetails?: {
    selectedProtocols?: unknown[] | null;
    detailedProtocol?: string | null;
  } | null;
};

export const CHIEF_COMPLAINT_REQUIRED = "Chief complaint is required";
export const PROTOCOL_REQUIRED = "Select at least one protocol";

export function hasChiefComplaint(values: SessionStep0Values): boolean {
  return (values.medicalCondition ?? "").trim().length > 0;
}

export function hasProtocol(values: SessionStep0Values): boolean {
  const healingDetails = values.healingDetails ?? {};
  const selected = Array.isArray(healingDetails.selectedProtocols)
    ? healingDetails.selectedProtocols.length
    : 0;
  const detailed = (healingDetails.detailedProtocol ?? "").trim().length;
  return selected > 0 || detailed > 0;
}

/**
 * Returns a map of `{ formFieldName: message }` for every unmet requirement.
 * Empty object === the form may be submitted.
 */
export function sessionStep0Errors(
  values: SessionStep0Values
): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!hasChiefComplaint(values)) {
    errors.medicalCondition = CHIEF_COMPLAINT_REQUIRED;
  }
  if (!hasProtocol(values)) {
    errors["healingDetails.selectedProtocols"] = PROTOCOL_REQUIRED;
  }
  return errors;
}
