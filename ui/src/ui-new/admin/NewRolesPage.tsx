import { useCallback, useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { toast } from "sonner";
import { formatDistanceToNow } from "date-fns";
import { ArrowDown, ArrowUp, Pencil, Plus, Search, ShieldCheck, Trash2 } from "lucide-react";
import { useDeleteRole, useRbacRoles } from "../../hooks/admin/useRbacRoles";
import type { RoleSummary } from "../../hooks/admin/useRbacRoles";
import { useCan } from "../../hooks/useMyPermissions";
import { extractDetail } from "../../lib/utils";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";

const DEFAULT_SIZE = 20;
const SORT_ASC = "name,asc";
const SORT_DESC = "name,desc";
const SEARCH_DEBOUNCE_MS = 300;

function relative(iso: string): string {
  try {
    return formatDistanceToNow(new Date(iso), { addSuffix: true });
  } catch {
    return "—";
  }
}

/** New-view Roles list: same search, sort, paging and delete rules as the classic page, laid out as in the mockup. */
export default function NewRolesPage() {
  const navigate = useNavigate();
  const [sp, setSp] = useSearchParams();
  const search = sp.get("search") ?? "";
  const page = Number(sp.get("page") ?? 0);
  const size = Number(sp.get("size") ?? DEFAULT_SIZE);
  const sort = sp.get("sort") ?? SORT_ASC;
  const { data, isLoading, isError, refetch } = useRbacRoles({ search, page, size, sort });

  const [inputValue, setInputValue] = useState(search);
  useEffect(() => setInputValue(search), [search]);
  useEffect(() => {
    if (inputValue === search) return;
    const timer = setTimeout(() => {
      setSp((prev) => {
        const next = new URLSearchParams(prev);
        if (inputValue) next.set("search", inputValue);
        else next.delete("search");
        next.set("page", "0");
        return next;
      });
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [inputValue, search, setSp]);

  const sortDescending = sort === SORT_DESC;
  const toggleSort = useCallback(() => {
    setSp((prev) => {
      const next = new URLSearchParams(prev);
      next.set("sort", sortDescending ? SORT_ASC : SORT_DESC);
      next.set("page", "0");
      return next;
    });
  }, [setSp, sortDescending]);
  const goToPage = (target: number) =>
    setSp((prev) => {
      const next = new URLSearchParams(prev);
      next.set("page", String(target));
      return next;
    });

  const deleteRole = useDeleteRole();
  const can = useCan();
  const canCreate = can("ROLES", "CREATE");
  const canUpdate = can("ROLES", "UPDATE");
  const canDelete = can("ROLES", "DELETE");
  const [deleteTarget, setDeleteTarget] = useState<RoleSummary | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const closeDelete = () => {
    setDeleteTarget(null);
    setDeleteError(null);
  };
  const confirmDelete = () => {
    if (!deleteTarget) return;
    const name = deleteTarget.name;
    setDeleteError(null);
    deleteRole.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success(`Role "${name}" deleted.`);
        closeDelete();
      },
      onError: (error: unknown) => {
        const status = (error as { response?: { status?: number } })?.response?.status;
        if (status === 409) {
          setDeleteError(extractDetail(error) ?? "This change would leave nobody able to manage roles, so it was blocked.");
          return;
        }
        closeDelete();
        toast.error(extractDetail(error) || "Failed to delete role");
      },
    });
  };

  const goEdit = (id: string) => navigate(`/admin/roles/${id}/edit`);
  const goNew = () => navigate("/admin/roles/new");
  const rows = data?.content ?? [];
  const totalPages = data ? data.totalPages || 1 : 1;
  const from = data && data.totalElements ? data.page * size + 1 : 0;
  const to = data ? data.page * size + rows.length : 0;

  return (
    <div className="hlx">
      <div className="page-head">
        <div>
          <h1>Roles &amp; permissions</h1>
          <p className="muted" style={{ marginTop: 6 }}>Who can do what in the admin screens.</p>
        </div>
        {canCreate && (
          <button className="btn btn-primary" type="button" onClick={goNew}>
            <Plus className="ico" /> New role
          </button>
        )}
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 14, flexWrap: "wrap" }}>
        <div style={{ position: "relative", flex: "1 1 280px", maxWidth: 380 }}>
          <Search className="ico" aria-hidden="true" style={{ position: "absolute", left: 12, top: 11, color: "var(--muted)" }} />
          <input className="input" style={{ paddingLeft: 38 }} placeholder="Search roles" aria-label="Search roles" value={inputValue} onChange={(e) => setInputValue(e.target.value)} />
        </div>
        {data && (
          <span className="small num ongr">
            {data.totalElements} role{data.totalElements === 1 ? "" : "s"}
          </span>
        )}
      </div>

      {isError ? (
        <div className="surface card" style={{ display: "flex", flexDirection: "column", gap: 12, alignItems: "flex-start" }}>
          <p className="err" style={{ margin: 0 }}>Couldn't load roles. Check your connection and try again.</p>
          <button className="btn btn-secondary" type="button" onClick={() => refetch()}>Retry</button>
        </div>
      ) : !isLoading && data && rows.length === 0 ? (
        <div className="surface card" style={{ textAlign: "center", display: "flex", flexDirection: "column", alignItems: "center", gap: 10, padding: 40 }}>
          <ShieldCheck className="ico" style={{ width: 36, height: 36, color: "var(--muted)" }} />
          <h2>{search ? `No roles match "${search}"` : "No roles yet"}</h2>
          <p className="muted" style={{ maxWidth: 440 }}>
            {search
              ? "Try a different name, or clear the search to see every role."
              : "Create your first role to give admin and ops users scoped access. You choose the resources a role covers, then toggle the permissions it grants."}
          </p>
          {!search && canCreate && (
            <button className="btn btn-primary" type="button" onClick={goNew}>
              <Plus className="ico" /> New role
            </button>
          )}
        </div>
      ) : (
        <>
          <div className="surface t-wrap">
            <table className="t">
              <thead>
                <tr>
                  <th aria-sort={sortDescending ? "descending" : "ascending"}>
                    <button type="button" onClick={toggleSort} style={{ all: "unset", cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 4 }}>
                      Role {sortDescending ? <ArrowDown style={{ width: 13, height: 13 }} /> : <ArrowUp style={{ width: 13, height: 13 }} />}
                    </button>
                  </th>
                  <th>Resources</th>
                  <th>Users</th>
                  <th>Updated</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {isLoading && !data && (
                  <tr><td colSpan={5} className="muted">Loading…</td></tr>
                )}
                {rows.map((r) => (
                  <tr key={r.id} style={{ cursor: "pointer" }} onClick={() => goEdit(r.id)}>
                    <td data-label="Role">
                      <div className="name">{r.name}</div>
                      <div className="small muted">{r.description && r.description.trim() ? r.description : "—"}</div>
                    </td>
                    <td data-label="Resources"><span className="badge">{r.resourceCount}</span></td>
                    <td data-label="Users"><span className="num">{r.userCount}</span></td>
                    <td data-label="Updated"><span className="small muted">{relative(r.updatedAt)}</span></td>
                    <td className="r" data-label="" onClick={(e) => e.stopPropagation()}>
                      <div style={{ display: "flex", gap: 2, justifyContent: "flex-end" }}>
                        {canUpdate && (
                          <button className="icon-btn" type="button" aria-label={`Edit ${r.name}`} onClick={() => goEdit(r.id)}>
                            <Pencil className="ico" />
                          </button>
                        )}
                        {canDelete && (
                          <button
                            className="icon-btn danger"
                            type="button"
                            aria-label={`Delete ${r.name}`}
                            onClick={() => {
                              setDeleteError(null);
                              setDeleteTarget(r);
                            }}
                          >
                            <Trash2 className="ico" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data && (
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 4px" }}>
              <span className="small num ongr">
                Showing {from}-{to} of {data.totalElements}
              </span>
              <div style={{ display: "flex", gap: 8 }}>
                <button className="btn btn-secondary" type="button" style={{ height: 36 }} aria-label="Previous page" disabled={data.page === 0} onClick={() => goToPage(data.page - 1)}>
                  Previous
                </button>
                <button className="btn btn-secondary" type="button" style={{ height: 36 }} aria-label="Next page" disabled={data.page + 1 >= totalPages} onClick={() => goToPage(data.page + 1)}>
                  Next
                </button>
              </div>
            </div>
          )}
        </>
      )}

      {deleteTarget && (
        <ConfirmDialog
          open
          onOpenChange={(next) => {
            if (!next) closeDelete();
          }}
          title={`Delete "${deleteTarget.name}"?`}
          description={
            <>
              {deleteTarget.userCount > 0
                ? `This role is assigned to ${deleteTarget.userCount} user${deleteTarget.userCount === 1 ? "" : "s"}. Deleting it will immediately remove their access to every resource it grants. This can't be undone.`
                : `"${deleteTarget.name}" will be permanently deleted. This can't be undone.`}
              {deleteError ? (
                <span role="alert" className="mt-3 block rounded-lg border border-destructive/20 bg-destructive/10 px-3 py-2 text-destructive">
                  {deleteError}
                </span>
              ) : null}
            </>
          }
          confirmLabel="Delete role"
          cancelLabel="Cancel"
          destructive
          loading={deleteRole.isPending}
          onConfirm={confirmDelete}
        />
      )}
    </div>
  );
}
