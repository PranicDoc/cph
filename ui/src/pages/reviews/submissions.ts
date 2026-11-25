/**
 * Each case's newest submission, keyed by case id. The list is newest first. The server submits by
 * case and acts on this one, so only it may offer a resubmit.
 */
export function newestByCase<T extends { caseId: string }>(newestFirst: T[]): Map<string, T> {
  return new Map([...newestFirst].reverse().map((s) => [s.caseId, s]));
}

/** Only a missing id means nobody is linked; an id without a name is an account that is gone. */
export function reviewerLabel(reviewerId: string | null, reviewerName: string | null): string {
  return reviewerId == null ? "No instructor linked" : reviewerName || "Unknown";
}
