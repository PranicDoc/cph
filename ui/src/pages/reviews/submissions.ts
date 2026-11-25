/**
 * Each case's newest submission, keyed by case id. The list is newest first. The server submits by
 * case and acts on this one, so only it may offer a resubmit.
 */
export function newestByCase<T extends { caseId: string }>(newestFirst: T[]): Map<string, T> {
  return new Map([...newestFirst].reverse().map((s) => [s.caseId, s]));
}
