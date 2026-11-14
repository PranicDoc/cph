/**
 * React-query keys for case-review data, shared by the pages that fetch it and the live
 * stream hook that invalidates it.
 */

/** Prefix of every per-submission and per-case review query. */
export const REVIEW_KEY = ["review"] as const;

/** One submission (healer or reviewer view). */
export const reviewSubmissionKey = (submissionId: string) => ["review", submissionId] as const;

/** Review status of the patients page's cases. */
export const CASE_STATUSES_KEY = ["review", "case-statuses"] as const;

/** The instructor's review queue. */
export const REVIEW_INBOX_KEY = ["review-inbox"] as const;

/** Review status summaries. */
export const REVIEW_STATUSES_KEY = ["review-statuses"] as const;
