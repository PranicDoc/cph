/**
 * WR-01 (Phase 42 gap closure): the previous `Feedback.tsx` code stored a
 * `string | null` (`searchParams.get("isFinal")`) directly into
 * `sessionStorage`, so an absent URL parameter persisted the literal
 * four-character string `"null"`, which is truthy when read back and
 * therefore submitted a `FINAL` qualitative-feedback record for a session
 * that was never marked final.
 *
 * Separately, `FeedbackLink.buildUrl` (the backend's only URL-building site)
 * emits `?token=...&caseId=...&final=<bool>` — the parameter name is `final`,
 * NOT `isFinal`. The component read `isFinal`, so on every real link today
 * the flag was always absent and always coerced to the bug above. Fixing
 * this by reading only `isFinal` would make `FINAL` unreachable — the
 * opposite bug. This module therefore accepts BOTH parameter names and
 * normalises to a real boolean exactly once.
 */

export const IS_FINAL_STORAGE_KEY = "isFinalFeedback";

/**
 * Reads the final-feedback flag from a URLSearchParams, checking the
 * backend's real parameter name (`final`) first and falling back to the
 * legacy name (`isFinal`) so links already in flight keep working. Returns
 * true only when the resulting value, lowercased and trimmed, is exactly
 * "true" — every other value (including null, "null", "", "yes") is false.
 * Fails closed: ambiguous input never creates a FINAL record.
 */
export function parseIsFinalParam(params: URLSearchParams): boolean {
  const raw = params.get("final") ?? params.get("isFinal");
  if (raw === null) {
    return false;
  }
  return raw.trim().toLowerCase() === "true";
}

/**
 * Computes parseIsFinalParam(params) and writes the literal "true" or
 * "false" string to storage under IS_FINAL_STORAGE_KEY, returning the
 * boolean it stored. Never writes anything other than "true"/"false" —
 * in particular, never the literal string "null".
 */
export function persistIsFinalFlag(
  params: URLSearchParams,
  storage: Storage
): boolean {
  const value = parseIsFinalParam(params);
  storage.setItem(IS_FINAL_STORAGE_KEY, value ? "true" : "false");
  return value;
}

/**
 * Reads the final-feedback flag for rendering/submission decisions. If
 * either parameter name is present on the URL, that takes precedence
 * (parsed via parseIsFinalParam). Otherwise falls back to storage, treating
 * anything other than the exact string "true" (including the legacy
 * corrupted literal "null") as false.
 */
export function readIsFinalFlag(
  params: URLSearchParams,
  storage: Storage
): boolean {
  if (params.get("final") !== null || params.get("isFinal") !== null) {
    return parseIsFinalParam(params);
  }
  return storage.getItem(IS_FINAL_STORAGE_KEY) === "true";
}
