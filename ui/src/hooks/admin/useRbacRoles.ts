import {
  useQuery,
  keepPreviousData,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import api from "../../lib/axios";
import type { SpringPageResponse } from "../../types/pagination";

// -- Interfaces -----------------------------------------------------------------

export interface RoleSummary {
  id: string;
  name: string;
  description: string | null;
  resourceCount: number;
  userCount: number;
}

/** D-04: grants travel resource-grouped, one entry per resource. */
export interface ResourceGrant {
  resource: string;
  operations: string[];
}

export interface RoleDetail {
  id: string;
  name: string;
  description: string | null;
  grants: ResourceGrant[];
}

export interface RoleWriteBody {
  name: string;
  description: string | null;
  grants: ResourceGrant[];
}

export interface RbacRolesParams {
  search: string;
  page: number;
  size: number;
  sort: string;
}

// -- Divergence rule (38-RESEARCH Pitfall 5, D-09) ----------------------------
//
// NONE of the mutations below declare an error handler, and NONE declare an
// optimistic cache write. A blanket error handler that raised a transient
// notification here would convert D-09's 409 SelfLockoutException -- which must
// render as an inline, action-blocking error at the call site -- into a
// disappearing message; a pre-settle cache write would make a rejected mutation
// look applied. Error branching (400 duplicate-name -> inline under the Name
// field; 409 self-lockout -> inline at the action site, action stays blocked;
// anything else -> a generic notification via extractDetail(e)) is the calling
// component's job. Success invalidates the roles list (and the role detail on
// update) and nothing else.

export function useRbacRoles(p: RbacRolesParams) {
  return useQuery<SpringPageResponse<RoleSummary>>({
    queryKey: ["rbac-roles", p],
    queryFn: () =>
      api.get("/api/admin/roles", { params: p }).then((r) => r.data),
    placeholderData: keepPreviousData,
  });
}

export function useRoleDetail(id: string | undefined) {
  return useQuery<RoleDetail>({
    queryKey: ["rbac-role", id],
    queryFn: () => api.get(`/api/admin/roles/${id}`).then((r) => r.data),
    enabled: !!id,
  });
}

export function useCreateRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: RoleWriteBody) =>
      api.post("/api/admin/roles", body).then((r) => r.data as RoleDetail),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rbac-roles"] });
    },
  });
}

export function useUpdateRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: RoleWriteBody }) =>
      api.put(`/api/admin/roles/${id}`, body).then((r) => r.data as RoleDetail),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: ["rbac-roles"] });
      qc.invalidateQueries({ queryKey: ["rbac-role", id] });
    },
  });
}

export function useDeleteRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete(`/api/admin/roles/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rbac-roles"] });
    },
  });
}
