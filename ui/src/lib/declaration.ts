export interface ConsentFormValues {
  consent1: boolean;
  consent2: boolean;
  consent3: boolean;
  patientSignature: string;
  /** Healer-only: "I have the patient's consent to fill in these details". */
  healerConsentAttestation?: boolean;
}

/**
 * The declaration sent with the case. When a healer fills the form in for the patient
 * (`onBehalf`), their consent confirmation is stored as `healerDeclaration`; a patient registering
 * themselves never sends it.
 */
export function buildDeclaration(values: ConsentFormValues, onBehalf: boolean) {
  return {
    patientConsent: values.consent1 && values.consent2 && values.consent3,
    healerDeclaration: onBehalf && values.healerConsentAttestation === true,
    patientSignature: values.patientSignature,
    signedAt: new Date().toISOString(),
  };
}
