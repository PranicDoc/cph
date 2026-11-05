export interface ConsentFormValues {
  consent1: boolean;
  consent2: boolean;
  consent3: boolean;
  patientSignature: string;
  /** Healer-only: "I have the patient's consent to fill in these details". */
  healerConsentAttestation?: boolean;
  /** Healer-only: the date the patient signed, as YYYY-MM-DD. */
  declarationDate?: string;
}

/** Today in the user's own calendar, as YYYY-MM-DD. */
export function todayAsDate(now: Date = new Date()): string {
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${m}-${d}`;
}

/**
 * The declaration sent with the case. When a healer fills the form in for the patient
 * (`onBehalf`), their consent confirmation is stored as `healerDeclaration` and they choose the
 * date; a patient registering themselves sends neither, and signs today.
 */
export function buildDeclaration(values: ConsentFormValues, onBehalf: boolean) {
  return {
    patientConsent: values.consent1 && values.consent2 && values.consent3,
    healerDeclaration: onBehalf && values.healerConsentAttestation === true,
    patientSignature: values.patientSignature,
    signedAt: onBehalf && values.declarationDate ? values.declarationDate : new Date().toISOString(),
  };
}
