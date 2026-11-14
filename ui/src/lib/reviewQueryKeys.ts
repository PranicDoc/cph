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

/** The instructor's queue filtered by status ("all" when unfiltered); under REVIEW_INBOX_KEY so the stream refetches it. */
export const reviewInboxKey = (status?: string) => ["review-inbox", status ?? "all"] as const;

/** One submission as the assigned instructor sees it; under the submission key so its events refetch it. */
export const reviewInboxItemKey = (submissionId: string) => ["review", submissionId, "inbox"] as const;

/**
 * The presigned PDF URL of one revision. Deliberately outside the "review" prefix: a live event
 * must not swap the URL under a PDF that is already on screen.
 */
export const reviewFileKey = (submissionId: string, revisionNo: number) =>
  ["review-file", submissionId, revisionNo] as const;
