/** Wire shapes of the case-review API (`/api/v1/reviews`), shared by several review hooks and screens. */

export type CaseReviewStatus =
  | "NOT_SUBMITTED"
  | "NOT_SUBMITTED_NO_REVIEWER"
  | "PENDING_REVIEW"
  | "CHANGES_REQUESTED"
  | "APPROVED"
  | "DISMISSED";

export interface CaseReviewStatusResponse {
  caseId: string;
  status: CaseReviewStatus;
  /** True when the case has a completed PDF that could be submitted. */
  eligible: boolean;
  latestRevisionNo: number | null;
}

export interface RevisionResponse {
  revisionNo: number;
  pdfJobId: string | null;
  submittedVia: "MANUAL" | "AUTO";
  submittedAt: string;
}

export type SubmissionOutcome = "CREATED" | "REVISION_ADDED" | "UNCHANGED" | "REREQUESTED";

export interface SubmissionResponse {
  id: string;
  caseId: string;
  healerId: string;
  /** Null once the instructor's account was erased, until a new one is linked. */
  reviewerId: string | null;
  stage: "REVIEWER" | "FEDERATION";
  status: "PENDING_REVIEW" | "CHANGES_REQUESTED" | "APPROVED" | "DISMISSED";
  dismissedFromApproved: boolean;
  dismissReason: string | null;
  submittedVia: "MANUAL" | "AUTO";
  latestRevisionNo: number;
  createdAt: string;
  updatedAt: string;
  revisions: RevisionResponse[];
  /** What a submit did; absent on plain reads. */
  outcome?: SubmissionOutcome | null;
  /** The case's medical condition, else "Case of <date>". */
  caseTitle: string;
  healerName: string | null;
  /** Null without a reviewer, and when the reviewer's account is gone: tell them apart by reviewerId. */
  reviewerName: string | null;
}

export type ReviewStatus = SubmissionResponse["status"];

export const STATUS_LABELS: Record<ReviewStatus, string> = {
  PENDING_REVIEW: "Pending review",
  CHANGES_REQUESTED: "Changes requested",
  APPROVED: "Approved",
  DISMISSED: "Dismissed",
};

/** Which party is calling; picks the route family (instructor inbox or healer submissions). */
export type ReviewSide = "reviewer" | "healer";

export interface ReviewComment {
  id: string;
  submissionId: string;
  /** The revision the comment (or its thread) is on, so the UI can say "on version N". */
  revisionNo: number;
  parentId: string | null;
  /** Null once the author's account was erased. */
  authorId: string | null;
  authorName: string | null;
  authorSide: "HEALER" | "REVIEWER";
  /** Set with x and y for a pin; x and y are 0-1 against the page's rendered size. */
  page: number | null;
  x: number | null;
  y: number | null;
  body: string;
  createdAt: string;
  resolvedAt: string | null;
}
