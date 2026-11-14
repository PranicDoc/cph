import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../../lib/axios";
import { extractDetail } from "../../lib/utils";

export interface ReviewerAssignment {
  healerId: string;
  healerName: string | null;
  reviewerId: string;
  reviewerName: string | null;
}

export interface HealerOption {
  id: string;
  fullName: string;
  email: string;
}

const BASE = "/api/v1/admin/reviewer-assignments";
const QK = ["reviewer-assignments"] as const;
const QK_ELIGIBLE = ["reviewer-assignments", "eligible"] as const;

// All four routes are SUPADM-only server-side.
export function useReviewerAssignments() {
  return useQuery<ReviewerAssignment[]>({
    queryKey: QK,
    queryFn: () => api.get(BASE).then((r) => r.data),
  });
}

/** Active healers who hold CASE:REVIEW: the only ones that can be picked as a reviewer. */
export function useEligibleReviewers() {
  return useQuery<HealerOption[]>({
    queryKey: QK_ELIGIBLE,
    queryFn: () => api.get(`${BASE}/eligible-reviewers`).then((r) => r.data),
  });
}

export function useAssignReviewer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ healerId, reviewerId }: { healerId: string; reviewerId: string }) =>
      api.put<ReviewerAssignment>(`${BASE}/${healerId}`, { reviewerId }).then((r) => r.data),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: QK });
      toast.success(`${data.healerName ?? "Healer"} is now reviewed by ${data.reviewerName ?? "the instructor"}`);
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to link the instructor", { duration: 5000 }),
  });
}

export function useUnassignReviewer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (healerId: string) => api.delete(`${BASE}/${healerId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK });
      toast.success("Instructor unlinked");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to unlink the instructor", { duration: 5000 }),
  });
}
