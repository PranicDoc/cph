// A HEALER cannot fetch the consent-type registry's admin-authored `label`
// field — GET /api/v1/consents/types is gated `hasAnyRole('ADMIN', 'SUPADM')`
// (see ConsentTypeController.java) — so a practitioner-facing screen must
// derive a display string from the consent-type key alone. This function
// stays pure: no network access, and no hardcoded list of which consent
// types exist, since the registry is dynamic per CONSENT-09.

const LOWERCASE_CONNECTORS = new Set([
  "and",
  "of",
  "the",
  "for",
  "to",
  "a",
  "an",
]);

export function formatConsentTypeLabel(key: string): string {
  const trimmed = key.trim();
  if (trimmed === "") return key;

  const words = trimmed.split(/[_-]+/).filter((word) => word.length > 0);

  return words
    .map((word, index) => {
      const lower = word.toLowerCase();
      if (index > 0 && LOWERCASE_CONNECTORS.has(lower)) {
        return lower;
      }
      return lower.charAt(0).toUpperCase() + lower.slice(1);
    })
    .join(" ");
}
