/**
 * React-query keys for case-review data, shared by the hooks that fetch it and the live stream
 * hook that invalidates it. Every key is under the ["review", ...] prefix except the review-file
 * key (see reviewFileKey).
 */

/** Prefix of every per-submission and per-case review query. */
export const REVIEW_KEY = ["review"] as const;

/** One submission (healer or reviewer view). */
export const reviewSubmissionKey = (submissionId: string) => ["review", submissionId] as const;

/** Review status of the patients page's cases. */
export const CASE_STATUSES_KEY = ["review", "case-statuses"] as const;

/** The instructor's review queue. */
export const REVIEW_INBOX_KEY = ["review", "inbox"] as const;

/** Review status summaries. */
export const REVIEW_STATUSES_KEY = ["review", "statuses"] as const;

/** The instructor's queue filtered by status ("all" when unfiltered); under REVIEW_INBOX_KEY so the stream refetches it. */
export const reviewInboxKey = (status?: string) => ["review", "inbox", status ?? "all"] as const;

/** One submission as the assigned instructor sees it; under the submission key so its events refetch it. */
export const reviewInboxItemKey = (submissionId: string) => ["review", submissionId, "inbox"] as const;

/**
 * The presigned PDF URL of one revision. Deliberately KEPT OUTSIDE the "review" prefix: a live
 * event must not swap the URL under a PDF that is already on screen.
 */
export const reviewFileKey = (submissionId: string, revisionNo: number) =>
  ["review-file", submissionId, revisionNo] as const;

/** Comments of one submission for one side; under the submission key so COMMENT_ADDED events refetch them. */
export const reviewCommentsKey = (submissionId: string, side: "reviewer" | "healer") =>
  ["review", submissionId, "comments", side] as const;

/** One submission as its healer sees it; under the submission key so its events refetch it. */
export const reviewHealerSubmissionKey = (submissionId: string) => ["review", submissionId, "healer"] as const;

/** The healer's own submissions list (My submissions); under the review prefix so any live event refetches it. */
export const MY_SUBMISSIONS_KEY = ["review", "my-submissions"] as const;

/** How many of the healer's cases are approved (dashboard tile). */
export const APPROVED_COUNT_KEY = ["review", "approved-count"] as const;

/** The super admin's list of every submission, one entry per filter combination. */
export const ALL_REVIEWS_KEY = ["review", "all"] as const;
export const allReviewsKey = (status?: string, reviewerId?: string, healerId?: string) =>
  ["review", "all", status ?? "all", reviewerId ?? "any", healerId ?? "any"] as const;
