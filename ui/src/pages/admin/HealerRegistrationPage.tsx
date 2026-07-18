import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import type { ColumnDef } from "@tanstack/react-table";
import { ChevronRight, ChevronLeft, UserPlus } from "lucide-react";
import { useAuth } from "../../context/AuthContext";
import {
  useAdminInvites,
  useCreateInvite,
  useResendInvite,
  useRevokeInvite,
  type AdminInvite,
} from "../../hooks/admin/useAdminInvites";
import { cn } from "../../lib/utils";
import { DataTable } from "@/components/ui/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { SelectField, TextField } from "@/components/form/Field";

const ROLE_OPTIONS = ["HEALER", "SENIOR_HEALER", "RECEPTIONIST", "ADMIN", "SUPADM"];

const STATUS_BADGE: Record<AdminInvite["status"], string> = {
  PENDING: "bg-amber-100 text-amber-700",
  EXPIRED: "bg-gray-100 text-gray-700",
  ACCEPTED: "bg-success/10 text-success",
  REVOKED: "bg-danger/10 text-danger",
};

function InviteStatusBadge({ status }: { status: AdminInvite["status"] }) {
  return (
    <Badge className={cn("font-semibold px-2 shrink-0", STATUS_BADGE[status])}>
      {status}
    </Badge>
  );
}

export default function HealerRegistrationPage() {
  const { user } = useAuth();
  const isSupadm = user?.role === "SUPADM";

  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useAdminInvites(page);

  const createInvite = useCreateInvite();
  const resendInvite = useResendInvite();
  const revokeInvite = useRevokeInvite();

  const [revokeTarget, setRevokeTarget] = useState<AdminInvite | null>(null);

  const roleOptions = isSupadm ? ROLE_OPTIONS : ROLE_OPTIONS.filter((r) => r !== "SUPADM");

  const inviteForm = useForm({
    defaultValues: {
      email: "",
      role: "HEALER",
    },
    onSubmit: async ({ value, formApi }) => {
      createInvite.mutate(
        {
          email: value.email,
          role: value.role,
        },
        {
          onSuccess: () => {
            formApi.reset();
          },
        }
      );
    },
  });

  const Pagination = () =>
    data ? (
      <div className="flex items-center justify-between mt-4 text-sm text-text-light">
        <span>{data.totalElements} invite{data.totalElements !== 1 ? "s" : ""}</span>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon-sm"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className="h-auto w-auto p-1 disabled:opacity-40 hover:bg-background"
          >
            <ChevronLeft className="w-4 h-4" />
          </Button>
          <span>{page + 1} / {data.totalPages || 1}</span>
          <Button
            variant="ghost"
            size="icon-sm"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="h-auto w-auto p-1 disabled:opacity-40 hover:bg-background"
          >
            <ChevronRight className="w-4 h-4" />
          </Button>
        </div>
      </div>
    ) : null;

  const canShowActions = (status: AdminInvite["status"]) => status === "PENDING" || status === "EXPIRED";

  const InviteActions = ({ invite }: { invite: AdminInvite }) => {
    if (!canShowActions(invite.status)) return null;
    return (
      <div className="flex items-center gap-3">
        <Button
          type="button"
          variant="link"
          onClick={() => resendInvite.mutate({ id: invite.id, email: invite.email })}
          disabled={resendInvite.isPending}
          className="h-auto p-0 text-xs font-medium"
        >
          Resend
        </Button>
        <Button
          type="button"
          variant="link"
          onClick={() => setRevokeTarget(invite)}
          className="h-auto p-0 text-xs font-medium text-danger hover:text-danger"
        >
          Revoke
        </Button>
      </div>
    );
  };

  const inviteColumns: ColumnDef<AdminInvite, unknown>[] = [
    {
      accessorKey: "email",
      header: "Email",
      cell: (info) => (
        <span className="font-medium text-text">{String(info.getValue())}</span>
      ),
    },
    {
      accessorKey: "role",
      header: "Role",
      cell: (info) => (
        <span className="text-text-light">{String(info.getValue())}</span>
      ),
    },
    {
      accessorKey: "status",
      header: "Status",
      cell: (info) => (
        <InviteStatusBadge status={info.getValue() as AdminInvite["status"]} />
      ),
    },
    {
      accessorKey: "createdAt",
      header: "Sent",
      cell: (info) => (
        <span className="text-text-light text-xs">
          {new Date(String(info.getValue())).toLocaleDateString()}
        </span>
      ),
    },
    {
      id: "actions",
      header: "Actions",
      cell: ({ row }) => <InviteActions invite={row.original} />,
    },
  ];

  return (
    <div>
      <h1 className="text-2xl font-semibold text-text mb-6">Healer Invitations</h1>

      {/* Create-invite form card */}
      <div className="bg-card border border-border rounded-xl shadow-sm p-6 mb-8">
        <h2 className="text-xl font-semibold text-text mb-4">Send a New Invite</h2>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            inviteForm.handleSubmit();
          }}
          className="space-y-4"
        >
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <Label className="block text-xs font-semibold text-text-light uppercase tracking-wide mb-1">
                Email
              </Label>
              <inviteForm.Field
                name="email"
                children={(field) => (
                  <TextField
                    field={field}
                    type="email"
                    placeholder="healer@example.com"
                    className="border px-3 py-2 bg-background"
                  />
                )}
              />
            </div>
            <div>
              <Label className="block text-xs font-semibold text-text-light uppercase tracking-wide mb-1">
                Role
              </Label>
              <inviteForm.Field
                name="role"
                children={(field) => (
                  <SelectField
                    field={field}
                    options={roleOptions.map((r) => ({ label: r, value: r }))}
                    className="border px-3 py-2 bg-background"
                  />
                )}
              />
            </div>
          </div>
          <Button
            type="submit"
            disabled={createInvite.isPending}
            className="font-semibold px-4 py-2.5 h-auto"
          >
            {createInvite.isPending ? "Sending…" : "Send Invite"}
          </Button>
        </form>
      </div>

      {/* Invite table */}
      {isLoading && <p className="text-text-light">Loading invites…</p>}

      {isError && (
        <div className="bg-card border border-border rounded-xl p-12 text-center">
          <h2 className="text-xl font-semibold text-text">Couldn't Load Invites</h2>
          <p className="text-sm text-text-light mt-2 max-w-sm mx-auto">
            Something went wrong while loading your invite list. Please refresh the page or try again in a moment.
          </p>
        </div>
      )}

      {data && data.content.length === 0 && page === 0 && (
        <div className="bg-card border border-border rounded-xl p-12 text-center">
          <div className="w-16 h-16 bg-primary/10 rounded-2xl flex items-center justify-center mx-auto">
            <UserPlus className="w-8 h-8 text-primary" />
          </div>
          <h2 className="text-xl font-semibold text-text mt-5">No Invites Sent Yet</h2>
          <p className="text-sm text-text-light mt-2 max-w-sm mx-auto">
            Invite a healer or staff member by email to get started. They'll receive a secure link to set up their
            account.
          </p>
        </div>
      )}

      {data && data.content.length > 0 && (
        <>
          {/* Desktop table */}
          <div className="hidden md:block">
            <DataTable<AdminInvite>
              columns={inviteColumns}
              data={data.content}
              enableSorting={false}
              headerRowClassName="bg-background hover:bg-background"
              headCellClassName="font-semibold"
              rowClassName="hover:bg-background/50"
            />
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-3">
            {data.content.map((invite) => (
              <div key={invite.id} className="border border-border rounded-xl p-4">
                <div className="flex items-start justify-between mb-2">
                  <span className="font-semibold text-text text-sm truncate">{invite.email}</span>
                  <InviteStatusBadge status={invite.status} />
                </div>
                <p className="text-xs text-text-light">{invite.role}</p>
                <p className="text-xs text-text-light mt-1">{new Date(invite.createdAt).toLocaleDateString()}</p>
                {canShowActions(invite.status) && (
                  <div className="mt-3">
                    <InviteActions invite={invite} />
                  </div>
                )}
              </div>
            ))}
          </div>

          <Pagination />
        </>
      )}

      <ConfirmDialog
        open={revokeTarget !== null}
        onOpenChange={(open) => !open && setRevokeTarget(null)}
        title="Revoke invite?"
        description={
          revokeTarget
            ? `The invite sent to ${revokeTarget.email} will stop working immediately.`
            : undefined
        }
        confirmLabel="Confirm Revoke"
        cancelLabel="Keep Invite"
        destructive
        loading={revokeInvite.isPending}
        onConfirm={() => {
          if (revokeTarget) {
            revokeInvite.mutate(revokeTarget.id, {
              onSettled: () => setRevokeTarget(null),
            });
          }
        }}
      />
    </div>
  );
}
