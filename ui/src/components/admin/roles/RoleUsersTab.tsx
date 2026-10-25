import { useCallback, useMemo, useState } from "react";
import type { ReactNode } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { toast } from "sonner";
import { UserPlus } from "lucide-react";
import { formatDistanceToNow } from "date-fns";

import {
  useRoleAssignments,
  useAssignUser,
  useUnassignUser,
} from "@/hooks/admin/useRoleAssignments";
import type { RoleAssignedUser } from "@/hooks/admin/useRoleAssignments";
import { useAdminHealers } from "@/hooks/admin/useAdminHealers";
import { useCan } from "@/hooks/useMyPermissions";
import { extractDetail } from "@/lib/utils";
import { useIsMobile } from "@/hooks/use-mobile";
import { DataTable } from "@/components/ui/data-table";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { UserPickerDialog } from "@/components/admin/roles/UserPickerDialog";

const SELF_LOCKOUT_FALLBACK =
  "This change would leave nobody able to manage roles, so it was blocked.";

function displayNameOf(user: { name: string | null; email: string | null }) {
  return user.name || user.email || "this user";
}

export interface RoleUsersTabProps {
  roleId: string;
  roleName: string;
}

/**
 * "Users assigned" tab of the Edit-role screen (mockup Screen 10). Lists the
 * users holding this role, adds users one request at a time via
 * {@link UserPickerDialog}, and removes an assignment behind a confirm that
 * surfaces a 409 self-lockout inline (D-09). No dirty state — its mutations
 * are immediate, so nothing here is discard-guarded.
 */
export function RoleUsersTab({ roleId, roleName }: RoleUsersTabProps) {
  const { data, isLoading, isError, refetch } = useRoleAssignments(roleId);
  // Assigning and removing users both need ROLES:UPDATE on the server.
  const canManage = useCan()("ROLES", "UPDATE");
  const healersQuery = useAdminHealers();
  const healers = useMemo(() => healersQuery.data ?? [], [healersQuery.data]);

  const assigned = useMemo(() => data ?? [], [data]);
  const alreadyAssigned = useMemo(
    () => assigned.map((user) => user.userId),
    [assigned],
  );

  const [pickerOpen, setPickerOpen] = useState(false);
  const assignUser = useAssignUser(roleId);

  const handleAssign = (userIds: string[]) => {
    setPickerOpen(false);
    userIds.forEach((userId) => {
      const healer = healers.find((h) => h.id === userId);
      const label = healer ? healer.fullName || healer.email : "User";
      assignUser.mutate(userId, {
        onSuccess: () => toast.success(`${label} assigned to "${roleName}".`),
        onError: (error) =>
          toast.error(extractDetail(error) || "Failed to assign user"),
      });
    });
  };

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
          <span className="font-semibold text-text">
            {info.row.original.name || info.row.original.email}
          </span>
        ),
      },
      {
        accessorKey: "email",
        header: "Email",
        cell: (info) => (
          <span className="block max-w-[280px] truncate text-text-light">
            {info.row.original.email}
          </span>
        ),
      },
      {
        accessorKey: "assignedAt",
        header: "Assigned",
        cell: (info) => (
          <span className="text-xs text-text-light">
            {formatDistanceToNow(new Date(info.row.original.assignedAt), {
              addSuffix: true,
            })}
          </span>
        ),
      },
      ...(canManage
        ? [{
            id: "actions",
            header: () => <div className="text-right" />,
            cell: (info) => {
              const user = info.row.original;
              return (
                <div className="flex justify-end">
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={`Remove ${displayNameOf(user)}`}
                    onClick={() => openRemove(user)}
                  >
                    Remove
                  </Button>
                </div>
              );
            },
          } satisfies ColumnDef<RoleAssignedUser, unknown>]
        : []),
    ],
    [openRemove, canManage],
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
          Assign a user to give them everything this role grants. Use Assign user
          to search by name or email.
        </p>
        {canManage && (
          <Button onClick={() => setPickerOpen(true)}>
            <UserPlus className="h-4 w-4" />
            Assign user
          </Button>
        )}
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
              {canManage && (
                <Button
                  variant="ghost"
                  size="sm"
                  aria-label={`Remove ${displayNameOf(user)}`}
                  onClick={() => openRemove(user)}
                >
                  Remove
                </Button>
              )}
            </div>
            <p className="truncate text-xs text-text-light">{user.email}</p>
            <p className="mt-1 text-xs text-text-light">
              Assigned{" "}
              {formatDistanceToNow(new Date(user.assignedAt), {
                addSuffix: true,
              })}
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

  const count = assigned.length;

  return (
    <div>
      <div className="mb-3.5 flex items-center justify-between">
        <div className="text-sm text-text-light">
          {count} user{count === 1 ? "" : "s"} {count === 1 ? "has" : "have"} this
          role
        </div>
        {canManage && (
          <Button variant="outline" size="sm" onClick={() => setPickerOpen(true)}>
            <UserPlus className="h-4 w-4" />
            Assign user
          </Button>
        )}
      </div>

      {content}

      <UserPickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        healers={healers}
        alreadyAssigned={alreadyAssigned}
        onConfirm={handleAssign}
        roleName={roleName}
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
