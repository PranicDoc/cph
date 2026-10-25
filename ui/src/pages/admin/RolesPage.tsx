import { useCallback, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { versioned } from "../../ui-version";
import type { ColumnDef } from "@tanstack/react-table";
import { toast } from "sonner";
import { formatDistanceToNow } from "date-fns";
import {
  ArrowDown,
  ArrowUp,
  ChevronLeft,
  ChevronRight,
  MoreVertical,
  Plus,
  Search,
  ShieldCheck,
  Trash2,
} from "lucide-react";

import { useRbacRoles, useDeleteRole } from "../../hooks/admin/useRbacRoles";
import { useCan } from "../../hooks/useMyPermissions";
import type { RoleSummary } from "../../hooks/admin/useRbacRoles";
import { extractDetail } from "../../lib/utils";
import { useIsMobile } from "../../hooks/use-mobile";
import { DataTable } from "@/components/ui/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

// D-08: URL query params are the single source of truth for the server-side
// searched / sorted / paginated list. Defaults: page=0, size=20, sort=name,asc.
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

function ClassicRolesPage() {
  const navigate = useNavigate();
  const [sp, setSp] = useSearchParams();

  const search = sp.get("search") ?? "";
  const page = Number(sp.get("page") ?? 0);
  const size = Number(sp.get("size") ?? DEFAULT_SIZE);
  const sort = sp.get("sort") ?? SORT_ASC;

  const { data, isLoading, isError, refetch } = useRbacRoles({
    search,
    page,
    size,
    sort,
  });

  const [inputValue, setInputValue] = useState(search);
  useEffect(() => {
    setInputValue(search);
  }, [search]);

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

  const goToPage = (target: number) => {
    setSp((prev) => {
      const next = new URLSearchParams(prev);
      next.set("page", String(target));
      return next;
    });
  };

  const deleteRole = useDeleteRole();
  const can = useCan();
  const canCreate = can("ROLES", "CREATE");
  const canUpdate = can("ROLES", "UPDATE");
  const canDelete = can("ROLES", "DELETE");
  const [deleteTarget, setDeleteTarget] = useState<RoleSummary | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const openDelete = useCallback((role: RoleSummary) => {
    setDeleteError(null);
    setDeleteTarget(role);
  }, []);

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
        setDeleteTarget(null);
        setDeleteError(null);
      },
      onError: (error: unknown) => {
        const status = (error as { response?: { status?: number } })?.response
          ?.status;
        if (status === 409) {
          setDeleteError(
            extractDetail(error) ??
              "This change would leave nobody able to manage roles, so it was blocked.",
          );
          return;
        }
        setDeleteTarget(null);
        setDeleteError(null);
        toast.error(extractDetail(error) || "Failed to delete role");
      },
    });
  };

  const goEdit = useCallback(
    (id: string) => navigate(`/admin/roles/${id}/edit`),
    [navigate],
  );
  const goNew = () => navigate("/admin/roles/new");

  const isMobile = useIsMobile();
  const rows = data?.content ?? [];

  const columns = useMemo<ColumnDef<RoleSummary, unknown>[]>(
    () => [
      {
        accessorKey: "name",
        header: () => (
          <button
            type="button"
            onClick={toggleSort}
            aria-sort={sortDescending ? "descending" : "ascending"}
            className="inline-flex items-center gap-1 uppercase tracking-wide"
          >
            Role
            {sortDescending ? (
              <ArrowDown className="h-3 w-3" />
            ) : (
              <ArrowUp className="h-3 w-3" />
            )}
          </button>
        ),
        cell: (info) => {
          const r = info.row.original;
          return (
            <div>
              <div className="text-[13.5px] font-semibold text-text">
                {r.name}
              </div>
              <div className="text-xs text-text-light">
                {r.description && r.description.trim() ? r.description : "—"}
              </div>
            </div>
          );
        },
      },
      {
        accessorKey: "resourceCount",
        header: () => <div className="text-right">Resources</div>,
        cell: (info) => (
          <div className="text-right">
            <Badge variant="default">{info.row.original.resourceCount}</Badge>
          </div>
        ),
      },
      {
        accessorKey: "userCount",
        header: () => <div className="text-right">Users</div>,
        cell: (info) => (
          <div className="text-right">
            <Badge variant="neutral">{info.row.original.userCount}</Badge>
          </div>
        ),
      },
      {
        accessorKey: "updatedAt",
        header: "Updated",
        cell: (info) => (
          <span className="text-xs text-text-light">
            {relative(info.row.original.updatedAt)}
          </span>
        ),
      },
      {
        id: "actions",
        header: () => <div className="text-right" />,
        cell: (info) => {
          const role = info.row.original;
          return (
            <div
              className="flex items-center justify-end gap-1"
              onClick={(event) => event.stopPropagation()}
            >
              {canUpdate && (
                <Button
                  variant="ghost"
                  size="sm"
                  aria-label={`Edit ${role.name}`}
                  onClick={() => goEdit(role.id)}
                >
                  Edit
                </Button>
              )}
              {canDelete && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="text-destructive"
                  aria-label={`Delete ${role.name}`}
                  onClick={() => openDelete(role)}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              )}
            </div>
          );
        },
      },
    ],
    [sortDescending, toggleSort, goEdit, openDelete, canUpdate, canDelete],
  );

  const totalPages = data ? data.totalPages || 1 : 1;

  const pagination = data ? (
    <div className="mt-4 flex items-center justify-between text-sm text-text-light">
      <span>
        {data.totalElements} role{data.totalElements === 1 ? "" : "s"}
      </span>
      <div className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="Previous page"
          disabled={data.page === 0}
          onClick={() => goToPage(data.page - 1)}
          className="h-auto w-auto p-1 disabled:opacity-40"
        >
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <span>
          {data.page + 1} / {totalPages}
        </span>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="Next page"
          disabled={data.page + 1 >= totalPages}
          onClick={() => goToPage(data.page + 1)}
          className="h-auto w-auto p-1 disabled:opacity-40"
        >
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  ) : null;

  let content: ReactNode;
  if (isError) {
    content = (
      <div className="flex flex-col items-start gap-3 rounded-xl border border-destructive/20 bg-destructive/10 px-4 py-6">
        <p className="text-sm text-destructive">
          Couldn't load roles. Check your connection and try again.
        </p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          Retry
        </Button>
      </div>
    );
  } else if (!isLoading && data && rows.length === 0) {
    content = search ? (
      <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
        <ShieldCheck className="h-10 w-10 text-text-light" />
        <h2 className="text-sm font-medium text-text">
          No roles match "{search}"
        </h2>
        <p className="max-w-sm text-sm text-text-light">
          Try a different name, or clear the search to see every role.
        </p>
      </div>
    ) : (
      <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
        <ShieldCheck className="h-10 w-10 text-text-light" />
        <h2 className="text-sm font-medium text-text">No roles yet</h2>
        <p className="max-w-md text-sm text-text-light">
          Create your first role to give admin and ops users scoped access. You
          choose the resources a role covers, then toggle the permissions it
          grants.
        </p>
        {canCreate && (
          <Button onClick={goNew}>
            <Plus className="h-4 w-4" />
            New role
          </Button>
        )}
      </div>
    );
  } else if (isMobile) {
    content = (
      <>
        <div className="space-y-3">
          {rows.map((role) => (
            <div
              key={role.id}
              onClick={() => goEdit(role.id)}
              className="cursor-pointer rounded-xl border border-border p-4 transition-colors active:bg-muted/60"
            >
              <div className="mb-2 flex items-start justify-between gap-2">
                <span className="text-sm font-semibold text-text">
                  {role.name}
                </span>
                {(canUpdate || canDelete) && <div onClick={(event) => event.stopPropagation()}>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`Actions for ${role.name}`}
                      >
                        <MoreVertical className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      {canUpdate && (
                        <DropdownMenuItem onClick={() => goEdit(role.id)}>
                          Edit
                        </DropdownMenuItem>
                      )}
                      {canDelete && (
                        <DropdownMenuItem
                          className="text-destructive"
                          onClick={() => openDelete(role)}
                        >
                          Delete
                        </DropdownMenuItem>
                      )}
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>}
              </div>
              <p className="mb-3 truncate text-xs text-text-light">
                {role.description && role.description.trim()
                  ? role.description
                  : "—"}
              </p>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="rounded-lg bg-muted/50 px-2 py-1.5 text-center">
                  <div className="text-text-light">Resources</div>
                  <div className="font-semibold text-text">
                    {role.resourceCount}
                  </div>
                </div>
                <div className="rounded-lg bg-muted/50 px-2 py-1.5 text-center">
                  <div className="text-text-light">Users</div>
                  <div className="font-semibold text-text">{role.userCount}</div>
                </div>
              </div>
              <p className="mt-2 text-[11px] text-text-light">
                Updated {relative(role.updatedAt)}
              </p>
            </div>
          ))}
        </div>
        {pagination}
      </>
    );
  } else {
    content = (
      <>
        <DataTable<RoleSummary>
          columns={columns}
          data={rows}
          isLoading={isLoading && !data}
          enableSorting={false}
          loadingRowCount={8}
          emptyMessage="No roles yet"
          onRowClick={(row) => goEdit(row.original.id)}
        />
        {pagination}
      </>
    );
  }

  return (
    <div>
      <div className="mb-1.5 text-xs text-text-light">Admin</div>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold text-text md:text-2xl">
          Roles &amp; Permissions
        </h1>
        {canCreate && (
          <Button onClick={goNew}>
            <Plus className="h-4 w-4" />
            New role
          </Button>
        )}
      </div>

      <div className="mb-4 flex items-center gap-3">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-light" />
          <Input
            className="pl-9"
            placeholder="Search roles"
            value={inputValue}
            onChange={(event) => setInputValue(event.target.value)}
          />
        </div>
        {data && (
          <span className="text-xs text-text-light">
            {data.totalElements} role{data.totalElements === 1 ? "" : "s"}
          </span>
        )}
      </div>

      {content}

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
                ? `This role is assigned to ${deleteTarget.userCount} user${
                    deleteTarget.userCount === 1 ? "" : "s"
                  }. Deleting it will immediately remove their access to every resource it grants. This can't be undone.`
                : `"${deleteTarget.name}" will be permanently deleted. This can't be undone.`}
              {deleteError ? (
                <span
                  role="alert"
                  className="mt-3 block rounded-lg border border-destructive/20 bg-destructive/10 px-3 py-2 text-destructive"
                >
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

const RolesPage = versioned(ClassicRolesPage, () => import("../../ui-new/admin/NewRolesPage"));
export default RolesPage;
