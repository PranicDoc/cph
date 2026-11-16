import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { MY_SUBMISSIONS_KEY } from "../lib/reviewQueryKeys";
import type { SubmissionResponse } from "../types/review";

/** Every submission of the signed-in healer, newest first, each with its revisions. */
export function useMySubmissions() {
  return useQuery<SubmissionResponse[]>({
    queryKey: MY_SUBMISSIONS_KEY,
    queryFn: () => api.get("/api/v1/reviews/my-submissions").then((r) => r.data),
  });
}
