import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../../lib/axios";
import { extractDetail } from "../../lib/utils";
import type { PageResponse } from "./useAdminAccounts";

export interface AdminInvite {
  id: string;
  email: string;
  role: string;
  clinicId: string;
  status: "PENDING" | "EXPIRED" | "ACCEPTED" | "REVOKED";
  createdAt: string;
  expiresAt: string;
}

export interface InviteCreatePayload {
  email: string;
  role: string;
  clinicId?: string;
}

const QK = ["admin-invites"] as const;

export function useAdminInvites(page = 0, size = 20) {
  return useQuery<PageResponse<AdminInvite>>({
    queryKey: [...QK, page, size],
    queryFn: () =>
      api
        .get("/api/admin/invites", {
          params: { page, size },
        })
        .then((r) => r.data),
  });
}

export function useCreateInvite() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: InviteCreatePayload) =>
      api.post<AdminInvite>("/api/admin/invites", payload).then((r) => r.data),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: QK });
      toast.success(`Invite sent to ${variables.email}`);
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to send invite. Please try again.", { duration: 5000 }),
  });
}

export function useResendInvite() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id }: { id: string; email: string }) =>
      api.post<AdminInvite>(`/api/admin/invites/${id}/resend`).then((r) => r.data),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: QK });
      toast.success(`Invite resent — new link sent to ${variables.email}`);
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to resend invite", { duration: 5000 }),
  });
}

export function useRevokeInvite() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api.post<AdminInvite>(`/api/admin/invites/${id}/revoke`).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK });
      toast.success("Invite revoked");
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to revoke invite", { duration: 5000 }),
  });
}
