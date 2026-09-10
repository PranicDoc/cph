import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api from "../../lib/axios";

// -- Interfaces -----------------------------------------------------------------

export interface RoleAssignedUser {
  userId: string;
  name: string | null;
  email: string | null;
  assignedAt: string;
}

// -- Divergence rule (38-RESEARCH Pitfall 5, D-09) ----------------------------
//
// Same as useRbacRoles: the assign / unassign mutations declare no error handler
// and no optimistic cache write, so a 409 SelfLockoutException on unassign
// reaches `mutation.error` intact and RoleAssignmentPage renders it inline while
// keeping the removal blocked. This file imports no notification helper. Every
// successful mutation invalidates BOTH the assignment list for this role AND the
// rbac-roles list, so the list screen's userCount badge refreshes.

export function useRoleAssignments(roleId: string | undefined) {
  return useQuery<RoleAssignedUser[]>({
    queryKey: ["role-assignments", roleId],
    queryFn: () =>
      api.get(`/api/admin/roles/${roleId}/users`).then((r) => r.data),
    enabled: !!roleId,
  });
}

export function useAssignUser(roleId: string) {
  const qc = useQueryClient();
  return useMutation({
    // UI-SPEC loops one POST per selected user client-side -- single-user hook,
    // no batch endpoint.
    mutationFn: (userId: string) =>
      api.post(`/api/admin/roles/${roleId}/users`, { userId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["role-assignments", roleId] });
      qc.invalidateQueries({ queryKey: ["rbac-roles"] });
    },
  });
}

export function useUnassignUser(roleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) =>
      api.delete(`/api/admin/roles/${roleId}/users/${userId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["role-assignments", roleId] });
      qc.invalidateQueries({ queryKey: ["rbac-roles"] });
    },
  });
}
