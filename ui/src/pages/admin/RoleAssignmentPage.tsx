import { useCallback, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate, useParams } from "react-router-dom";
import type { ColumnDef } from "@tanstack/react-table";
import { toast } from "sonner";
import { ChevronLeft, Trash2, UserPlus } from "lucide-react";

import {
  useRoleAssignments,
  useAssignUser,
  useUnassignUser,
} from "../../hooks/admin/useRoleAssignments";
import type { RoleAssignedUser } from "../../hooks/admin/useRoleAssignments";
import { useRoleDetail } from "../../hooks/admin/useRbacRoles";
import { useAdminHealers } from "../../hooks/admin/useAdminHealers";
import { extractDetail } from "../../lib/utils";
import { useIsMobile } from "../../hooks/use-mobile";
import { DataTable } from "@/components/ui/data-table";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { UserPickerDialog } from "@/components/admin/roles/UserPickerDialog";

const SELF_LOCKOUT_FALLBACK =
  "This change would leave nobody able to manage roles, so it was blocked.";

function displayNameOf(user: {
  name: string | null;
  email: string | null;
}): string {
  return user.name || user.email || "this user";
}

/**
 * UI-SPEC Screen E — the role -> user assignment page at
 * `/admin/roles/:id/users`. Lists assigned users with their assigned date,
 * adds users one request at a time through {@link UserPickerDialog}, and
 * removes an assignment behind a confirmation that surfaces a 409 self-lockout
 * inline (D-09) without touching the assignment.
 *
 * This page has NO dirty form state and is deliberately not discard-guarded
 * (UI-SPEC Screen D exclusion) — it wires up no unsaved-changes navigation
 * blocker. Route registration is plan 38-10's job; the page is testable in
 * isolation via `renderWithDataRouter`.
 */
export default function RoleAssignmentPage() {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const roleId = id ?? "";

  const roleDetail = useRoleDetail(roleId);
  const roleName = roleDetail.data?.name ?? "Role";

  const { data, isLoading, isError, refetch } = useRoleAssignments(roleId);
  const healersQuery = useAdminHealers();
  const healers = useMemo(() => healersQuery.data ?? [], [healersQuery.data]);

  const assigned = useMemo(() => data ?? [], [data]);
  const alreadyAssigned = useMemo(
    () => assigned.map((user) => user.userId),
    [assigned],
  );

  // -- Add flow --------------------------------------------------------------
  // The endpoint is deliberately single-user (no batch API), so a confirmed
  // picker fires one useAssignUser mutation per selected id. The dialog closes
  // immediately; each result reports independently so a single failure is
  // never silently dropped. There is no pre-settle cache write anywhere — the
  // list only changes after a 2xx via the hook's invalidation.
  const [pickerOpen, setPickerOpen] = useState(false);
  const assignUser = useAssignUser(roleId);

  const handleAssign = (userIds: string[]) => {
    setPickerOpen(false);
    userIds.forEach((userId) => {
      const healer = healers.find((h) => h.id === userId);
      const label = healer ? healer.fullName || healer.email : "User";
      assignUser.mutate(userId, {
        onSuccess: () => {
          toast.success(`${label} assigned to "${roleName}".`);
        },
        onError: (error) => {
          toast.error(extractDetail(error) || "Failed to assign user");
        },
      });
    });
  };

  // -- Remove flow ---------------------------------------------------------
  // The hook carries NO onError (D-09), so this component owns the branching:
  //   409  -> keep the dialog open, render the server detail verbatim inline in
  //           a role="alert" block, leave the row present, no success toast, no
  //           navigation, no optimistic removal.
  //   else -> close the dialog and toast.error.
  const unassignUser = useUnassignUser(roleId);
  const [removeTarget, setRemoveTarget] = useState<RoleAssignedUser | null>(null);
  const [removeError, setRemoveError] = useState<string | null>(null);

  const openRemove = useCallback((user: RoleAssignedUser) => {
    setRemoveError(null);
    setRemoveTarget(user);
  }, []);

  const closeRemove = () => {
    setRemoveTarget(null);
    setRemoveError(null);
  };

  const confirmRemove = () => {
    if (!removeTarget) return;
    const target = removeTarget;
    const label = displayNameOf(target);
    setRemoveError(null);
    unassignUser.mutate(target.userId, {
      onSuccess: () => {
        toast.success(`${label} removed from "${roleName}".`);
        setRemoveTarget(null);
        setRemoveError(null);
      },
      onError: (error: unknown) => {
        const status = (error as { response?: { status?: number } })?.response
          ?.status;
        if (status === 409) {
          setRemoveError(extractDetail(error) ?? SELF_LOCKOUT_FALLBACK);
          return;
        }
        setRemoveTarget(null);
        setRemoveError(null);
        toast.error(extractDetail(error) || "Failed to remove user");
      },
    });
  };

  const columns = useMemo<ColumnDef<RoleAssignedUser, unknown>[]>(
    () => [
      {
        accessorKey: "name",
        header: "Name",
        cell: (info) => (
          <span className="font-medium text-text">
            {info.row.original.name || info.row.original.email}
          </span>
        ),
      },
      {
        accessorKey: "email",
        header: "Email",
        cell: (info) => (
          <span className="block max-w-[280px] truncate text-xs text-text-light">
            {info.row.original.email}
          </span>
        ),
      },
      {
        accessorKey: "assignedAt",
        header: "Assigned",
        cell: (info) => (
          <span className="text-xs text-text-light">
            {new Date(info.row.original.assignedAt).toLocaleDateString()}
          </span>
        ),
      },
      {
        id: "actions",
        header: () => <div className="text-right">Actions</div>,
        cell: (info) => {
          const user = info.row.original;
          return (
            <div className="flex items-center justify-end">
              <Button
                variant="ghost"
                size="icon-sm"
                className="text-destructive"
                aria-label={`Remove ${displayNameOf(user)}`}
                onClick={() => openRemove(user)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          );
        },
      },
    ],
    [openRemove],
  );

  const isMobile = useIsMobile();

  let content: ReactNode;
  if (isError) {
    content = (
      <div className="flex flex-col items-start gap-3 rounded-xl border border-destructive/20 bg-destructive/10 px-4 py-6">
        <p className="text-sm text-destructive">
          Couldn't load the users assigned to this role.
        </p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          Retry
        </Button>
      </div>
    );
  } else if (!isLoading && data && assigned.length === 0) {
    content = (
      <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
        <UserPlus className="h-10 w-10 text-text-light" />
        <h2 className="text-sm font-medium text-text">No users assigned</h2>
        <p className="max-w-md text-sm text-text-light">
          Assign a user to give them everything this role grants. Use Add users
          to search by name or email.
        </p>
        <Button onClick={() => setPickerOpen(true)}>
          <UserPlus className="h-4 w-4" />
          Add users
        </Button>
      </div>
    );
  } else if (isMobile) {
    content = (
      <div className="space-y-3">
        {assigned.map((user) => (
          <div key={user.userId} className="rounded-xl border border-border p-4">
            <div className="mb-1 flex items-start justify-between gap-2">
              <span className="text-sm font-semibold text-text">
                {user.name || user.email}
              </span>
              <Button
                variant="ghost"
                size="icon-sm"
                className="text-destructive"
                aria-label={`Remove ${displayNameOf(user)}`}
                onClick={() => openRemove(user)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
            <p className="truncate text-xs text-text-light">{user.email}</p>
            <p className="mt-1 text-xs text-text-light">
              Assigned {new Date(user.assignedAt).toLocaleDateString()}
            </p>
          </div>
        ))}
      </div>
    );
  } else {
    content = (
      <DataTable<RoleAssignedUser>
        columns={columns}
        data={assigned}
        isLoading={isLoading && !data}
        enableSorting={false}
        loadingRowCount={5}
        emptyMessage="No users assigned"
      />
    );
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="Back to roles"
            onClick={() => navigate("/admin/roles")}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <h1 className="truncate text-xl font-bold text-text md:text-2xl">
            {roleName} — assigned users
          </h1>
        </div>
        <Button onClick={() => setPickerOpen(true)}>
          <UserPlus className="h-4 w-4" />
          Add users
        </Button>
      </div>

      {content}

      <UserPickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        healers={healers}
        alreadyAssigned={alreadyAssigned}
        onConfirm={handleAssign}
        loading={assignUser.isPending}
      />

      {removeTarget && (
        <ConfirmDialog
          open
          onOpenChange={(next) => {
            if (!next) closeRemove();
          }}
          title={`Remove ${displayNameOf(removeTarget)} from this role?`}
          description={
            <>
              They lose the permissions this role grants on their next request.
              {removeError ? (
                <span
                  role="alert"
                  className="mt-3 block rounded-lg border border-destructive/20 bg-destructive/10 px-3 py-2 text-destructive"
                >
                  {removeError}
                </span>
              ) : null}
            </>
          }
          confirmLabel="Remove"
          cancelLabel="Keep assignment"
          destructive
          loading={unassignUser.isPending}
          onConfirm={confirmRemove}
        />
      )}
    </div>
  );
}
