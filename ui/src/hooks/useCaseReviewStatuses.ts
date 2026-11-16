import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { CASE_STATUSES_KEY } from "../lib/reviewQueryKeys";
import type { CaseReviewStatusResponse } from "../types/review";

// The ids ride in the query string, so a long page of cases is asked for in chunks.
const STATUS_CHUNK = 50;

async function fetchCaseStatuses(caseIds: string[]): Promise<Record<string, CaseReviewStatusResponse>> {
  const out: Record<string, CaseReviewStatusResponse> = {};
  for (let i = 0; i < caseIds.length; i += STATUS_CHUNK) {
    const chunk = caseIds.slice(i, i + STATUS_CHUNK);
    const res = await api.get<CaseReviewStatusResponse[]>("/api/v1/reviews/case-statuses", {
      params: { caseIds: chunk.join(",") },
    });
    for (const status of res.data) out[status.caseId] = status;
  }
  return out;
}

/**
 * Review status of the caller's own cases, keyed by case id. Under the case-statuses key, so a live
 * STATUS_CHANGED or pdf-ready event refetches it. `scope` separates callers that ask for the same
 * ids; `retry: false` is opt-in for a check that must not delay the page.
 */
export function useCaseReviewStatuses(caseIds: string[], options?: { scope?: string; retry?: false }) {
  return useQuery({
    queryKey: options?.scope ? [...CASE_STATUSES_KEY, options.scope, caseIds] : [...CASE_STATUSES_KEY, caseIds],
    queryFn: () => fetchCaseStatuses(caseIds),
    enabled: caseIds.length > 0,
    ...(options?.retry === false ? { retry: false } : {}),
  });
}
