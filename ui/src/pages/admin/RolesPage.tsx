import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import type { ColumnDef } from "@tanstack/react-table";
import { toast } from "sonner";
import {
  ArrowDown,
  ArrowUp,
  ChevronLeft,
  ChevronRight,
  MoreVertical,
  Pencil,
  Plus,
  Search,
  ShieldCheck,
  Trash2,
  Users,
} from "lucide-react";

import { useRbacRoles, useDeleteRole } from "../../hooks/admin/useRbacRoles";
import type { RoleSummary } from "../../hooks/admin/useRbacRoles";
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

export default function RolesPage() {
  const navigate = useNavigate();
  const [sp, setSp] = useSearchParams();

  // The URL — NOT useState — owns page / size / sort. (This is where
  // AccountsPage diverges: it keeps `page` in local state.)
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

  // -- Debounced search ------------------------------------------------------
  // Local input state so keystrokes never hit the network; a 300ms timer
  // writes it into the URL. Every write of `search` also resets `page` to 0
  // (38-RESEARCH Pitfall 6) so a narrowed result set never lands on an empty
  // page.
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

  // -- Sort (Name header only) --------------------------------------------
  // DataTable runs with enableSorting={false}; this drives ?sort manually and
  // also resets page to 0.
  const sortDescending = sort === SORT_DESC;
  const toggleSort = () => {
    setSp((prev) => {
      const next = new URLSearchParams(prev);
      next.set("sort", sortDescending ? SORT_ASC : SORT_DESC);
      next.set("page", "0");
      return next;
    });
  };

  const goToPage = (target: number) => {
    setSp((prev) => {
      const next = new URLSearchParams(prev);
      next.set("page", String(target));
      return next;
    });
  };

  // -- Delete flow -------------------------------------------------------
  const deleteRole = useDeleteRole();
  const [deleteTarget, setDeleteTarget] = useState<RoleSummary | null>(null);

  const openDelete = (role: RoleSummary) => {
    setDeleteTarget(role);
  };

  const closeDelete = () => {
    setDeleteTarget(null);
  };

  const confirmDelete = () => {
    if (!deleteTarget) return;
    const name = deleteTarget.name;
    deleteRole.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success(`Role "${name}" deleted.`);
        setDeleteTarget(null);
      },
    });
  };

  const goEdit = (id: string) => navigate(`/admin/roles/${id}/edit`);
  const goUsers = (id: string) => navigate(`/admin/roles/${id}/users`);
  const goNew = () => navigate("/admin/roles/new");

  const isMobile = useIsMobile();
  const rows = data?.content ?? [];

  const columns: ColumnDef<RoleSummary, unknown>[] = [
    {
      accessorKey: "name",
      header: () => (
        <button
          type="button"
          onClick={toggleSort}
          aria-sort={sortDescending ? "descending" : "ascending"}
          className="inline-flex items-center gap-1 uppercase tracking-wide"
        >
          Name
          {sortDescending ? (
            <ArrowDown className="h-3 w-3" />
          ) : (
            <ArrowUp className="h-3 w-3" />
          )}
        </button>
      ),
      cell: (info) => (
        <span className="font-medium text-text">{info.row.original.name}</span>
      ),
    },
    {
      accessorKey: "description",
      header: "Description",
      cell: (info) => {
        const description = info.row.original.description;
        return (
          <span className="block max-w-[280px] truncate text-text-light">
            {description && description.trim() ? description : "—"}
          </span>
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
      id: "actions",
      header: () => <div className="text-right">Actions</div>,
      cell: (info) => {
        const role = info.row.original;
        return (
          <div
            className="flex items-center justify-end gap-1"
            onClick={(event) => event.stopPropagation()}
          >
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={`Edit ${role.name}`}
              onClick={() => goEdit(role.id)}
            >
              <Pencil className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={`Assigned users for ${role.name}`}
              onClick={() => goUsers(role.id)}
            >
              <Users className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              className="text-destructive"
              aria-label={`Delete ${role.name}`}
              onClick={() => openDelete(role)}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        );
      },
    },
  ];

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
        <Button onClick={goNew}>
          <Plus className="h-4 w-4" />
          Create role
        </Button>
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
                <div onClick={(event) => event.stopPropagation()}>
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
                      <DropdownMenuItem onClick={() => goEdit(role.id)}>
                        Edit
                      </DropdownMenuItem>
                      <DropdownMenuItem onClick={() => goUsers(role.id)}>
                        Assigned users
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        className="text-destructive"
                        onClick={() => openDelete(role)}
                      >
                        Delete
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
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
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-text md:text-2xl">Roles</h1>
        <Button onClick={goNew}>
          <Plus className="h-4 w-4" />
          Create role
        </Button>
      </div>

      <div className="relative mb-4 max-w-sm">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-light" />
        <Input
          className="pl-9"
          placeholder="Search roles"
          value={inputValue}
          onChange={(event) => setInputValue(event.target.value)}
        />
      </div>

      {content}

      {deleteTarget && (
        <ConfirmDialog
          open
          onOpenChange={(next) => {
            if (!next) closeDelete();
          }}
          title="Delete this role?"
          description={`"${deleteTarget.name}" will be permanently deleted and any user assigned to it loses the access it granted. This can't be undone.`}
          confirmLabel="Delete role"
          cancelLabel="Keep role"
          destructive
          loading={deleteRole.isPending}
          onConfirm={confirmDelete}
        />
      )}
    </div>
  );
}
