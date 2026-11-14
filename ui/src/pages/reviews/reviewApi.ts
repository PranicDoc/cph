import api from "../../lib/axios";

/** Shown when a healer tries to submit without a linked instructor. */
export const NO_REVIEWER_MESSAGE =
  "You don't have an instructor linked yet. Ask your administrator to link one before submitting.";

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
  reviewerId: string;
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
