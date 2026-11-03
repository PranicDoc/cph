import { useMutation } from "@tanstack/react-query";
import api from "../lib/axios";

export interface EraseResult {
  creditAccountErased: boolean;
}

/** Erases a healer account for good. `healerId` omitted = the signed-in healer erases their own account. */
export function useEraseAccount() {
  return useMutation({
    mutationFn: ({ healerId, confirmation }: { healerId?: string; confirmation: string }) =>
      (healerId
        ? api.post<EraseResult>(`/api/v1/healers/${healerId}/erase`, { confirmation })
        : api.post<EraseResult>("/api/v1/auth/erase-account", { confirmation })
      ).then((r) => r.data),
  });
}
