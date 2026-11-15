import api from "../../lib/axios";

/** Shown when a healer tries to submit without a linked instructor. */
export const NO_REVIEWER_MESSAGE =
  "You don't have an instructor linked yet. Ask your administrator to link one before submitting.";

/** Profile toggle: label and the note under it. */
export const AUTO_SUBMIT_LABEL = "Automatically submit PDFs for review after they are generated";
export const AUTO_SUBMIT_NOTE =
  "New PDFs, including amendments to cases already in review, are sent to your instructor automatically. Approved cases are never sent automatically: if you edit an approved case, submit it for review yourself.";

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
}

const BASE = "/api/v1/reviews";

/** Opens a review for the case, or adds a revision when one is already open. */
export async function submitCase(caseId: string): Promise<SubmissionResponse> {
  const res = await api.post<SubmissionResponse>(`${BASE}/cases/${caseId}/submit`);
  return res.data;
}

// The ids ride in the query string, so a long page of cases is asked for in chunks.
const STATUS_CHUNK = 50;

/** Review status of the caller's own cases, keyed by case id. */
export async function caseStatuses(caseIds: string[]): Promise<Record<string, CaseReviewStatusResponse>> {
  const out: Record<string, CaseReviewStatusResponse> = {};
  for (let i = 0; i < caseIds.length; i += STATUS_CHUNK) {
    const chunk = caseIds.slice(i, i + STATUS_CHUNK);
    const res = await api.get<CaseReviewStatusResponse[]>(`${BASE}/case-statuses`, {
      params: { caseIds: chunk.join(",") },
    });
    for (const status of res.data) out[status.caseId] = status;
  }
  return out;
}

/** Shown under the Case review toggle when the healer has no instructor. */
export const NO_INSTRUCTOR_LINE = "No instructor is linked to you yet.";

/** Turns opt-in auto-submit on or off for the signed-in healer. */
export async function updateReviewPreferences(autoSubmitForReview: boolean): Promise<void> {
  await api.put("/api/v1/auth/me/review-preferences", { autoSubmitForReview });
}

// ---- Instructor (CASE:REVIEW) side ----

export type ReviewStatus = SubmissionResponse["status"];

/** One row of the instructor's queue, oldest latest-revision first. */
export interface InboxItem {
  submissionId: string;
  healerName: string;
  caseTitle: string;
  latestRevisionNo: number;
  status: ReviewStatus;
  submittedAt: string | null;
}

/** The instructor's queue, optionally filtered by status. */
export async function inbox(status?: ReviewStatus): Promise<InboxItem[]> {
  const res = await api.get<InboxItem[]>(`${BASE}/inbox`, { params: status ? { status } : undefined });
  return res.data;
}

/** One assigned submission with its revisions. A 403 means it was moved to another instructor. */
export async function inboxItem(submissionId: string): Promise<SubmissionResponse> {
  const res = await api.get<SubmissionResponse>(`${BASE}/inbox/${submissionId}`);
  return res.data;
}

/** Short-lived presigned URL of one revision's PDF. */
export async function inboxFileUrl(submissionId: string, revisionNo: number): Promise<string> {
  const res = await api.get<{ url: string }>(`${BASE}/inbox/${submissionId}/revisions/${revisionNo}/file`);
  return res.data.url;
}

/** Approves the latest revision. A 409 means a newer version arrived; its detail says so. */
export async function approve(submissionId: string, revisionNo: number): Promise<SubmissionResponse> {
  const res = await api.post<SubmissionResponse>(`${BASE}/inbox/${submissionId}/approve`, { revisionNo });
  return res.data;
}

/** Asks the healer for changes on the latest revision. A 409 means a newer version arrived. */
export async function requestChanges(submissionId: string, revisionNo: number): Promise<SubmissionResponse> {
  const res = await api.post<SubmissionResponse>(`${BASE}/inbox/${submissionId}/request-changes`, { revisionNo });
  return res.data;
}

/** Shown before a healer edits a case whose review is approved. */
export const APPROVED_EDIT_MESSAGE =
  "This case is approved. Editing it will dismiss the approval and you will need a new review.";
export const APPROVED_EDIT_CONFIRM = "Edit and dismiss approval";

/** Shown in the viewer when the instructor loses the submission (reassigned) while it is open. */
export const MOVED_MESSAGE = "This submission was moved to another instructor";
/** The refresh banner shown when a newer revision arrives while one is open. */
export const NEW_VERSION_MESSAGE = "New version available — refresh";

export const STATUS_LABELS: Record<ReviewStatus, string> = {
  PENDING_REVIEW: "Pending review",
  CHANGES_REQUESTED: "Changes requested",
  APPROVED: "Approved",
  DISMISSED: "Dismissed",
};

// ---- Comments and threads ----

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

export interface NewComment {
  revisionNo?: number;
  parentId?: string;
  page?: number;
  x?: number;
  y?: number;
  body: string;
}

const commentsPath = (side: ReviewSide, submissionId: string) =>
  side === "reviewer" ? `${BASE}/inbox/${submissionId}/comments` : `${BASE}/submissions/${submissionId}/comments`;

export async function listComments(side: ReviewSide, submissionId: string): Promise<ReviewComment[]> {
  const res = await api.get<ReviewComment[]>(commentsPath(side, submissionId));
  return res.data;
}

export async function addComment(side: ReviewSide, submissionId: string, comment: NewComment): Promise<ReviewComment> {
  const res = await api.post<ReviewComment>(commentsPath(side, submissionId), comment);
  return res.data;
}

/** Instructor only. */
export async function resolveComment(commentId: string): Promise<void> {
  await api.post(`${BASE}/inbox/comments/${commentId}/resolve`);
}

// ---- Healer's own submission ----

/** One of the caller's own submissions with its revisions. */
export async function submission(submissionId: string): Promise<SubmissionResponse> {
  const res = await api.get<SubmissionResponse>(`${BASE}/submissions/${submissionId}`);
  return res.data;
}

/** Short-lived presigned URL of one revision's PDF, for the submitting healer. */
export async function submissionFileUrl(submissionId: string, revisionNo: number): Promise<string> {
  const res = await api.get<{ url: string }>(`${BASE}/submissions/${submissionId}/revisions/${revisionNo}/file`);
  return res.data.url;
}

/** The server's reason for a refused request (validation 400, stale 409), else a fallback. */
export function reviewErrorMessage(e: unknown, fallback: string): string {
  const detail = (e as { response?: { data?: { detail?: unknown } } })?.response?.data?.detail;
  return typeof detail === "string" && detail ? detail : fallback;
}

// ---- Healer status tracking ----

/** Every submission of the signed-in healer, newest first, each with its revisions. */
export async function mySubmissions(): Promise<SubmissionResponse[]> {
  const res = await api.get<SubmissionResponse[]>(`${BASE}/my-submissions`);
  return res.data;
}

/** How many of the signed-in healer's cases are approved. */
export async function myApprovedCount(): Promise<number> {
  const res = await api.get<{ count: number }>(`${BASE}/my-approved-count`);
  return res.data.count;
}
