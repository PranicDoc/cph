import { useQuery } from "@tanstack/react-query";
import api from "../../lib/axios";

// -- Interfaces -----------------------------------------------------------------

export interface CatalogVerb {
  value: string;
  label: string;
  /** One-line description rendered under the verb label in the edit grid. */
  description: string;
  /**
   * Structurally required verb — the grid shows it always-on and
   * non-removable (currently only ROLES:UPDATE). Server-side RoleManagerGuard
   * is the real self-lockout enforcement.
   */
  locked: boolean;
}

export interface RbacCatalogResource {
  resource: string;
  label: string;
  verbs: CatalogVerb[];
}

// -- Permission catalog -------------------------------------------------------
//
// The catalog is static, enum-derived data on the backend (Resource.values() x
// getSupportedOperations() + backend-owned labels) that cannot change without a
// redeploy, so it is read once and held with a 5-minute stale window. No
// mutations. The UI only *offers* allow-listed (resource, verb) toggles from
// this data -- Resource.supports(Operation) enforcement stays server-side (D-04).

export function useRbacCatalog() {
  return useQuery<RbacCatalogResource[]>({
    queryKey: ["rbac-catalog"],
    queryFn: () =>
      api.get("/api/admin/roles/catalog").then((r) => r.data),
    staleTime: 5 * 60_000,
  });
}
