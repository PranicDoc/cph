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
