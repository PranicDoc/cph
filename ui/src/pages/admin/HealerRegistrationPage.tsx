import { useState } from "react";
import { ChevronRight, ChevronLeft, UserPlus } from "lucide-react";
import { useAuth } from "../../context/AuthContext";
import { useAdminClinics } from "../../hooks/admin/useAdminClinics";
import {
  useAdminInvites,
  useCreateInvite,
  useResendInvite,
  useRevokeInvite,
  type AdminInvite,
} from "../../hooks/admin/useAdminInvites";
import { cn } from "../../lib/utils";

const ROLE_OPTIONS = ["HEALER", "SENIOR_HEALER", "RECEPTIONIST", "ADMIN", "SUPADM"];

const STATUS_BADGE: Record<AdminInvite["status"], string> = {
  PENDING: "bg-amber-100 text-amber-700",
  EXPIRED: "bg-gray-100 text-gray-700",
  ACCEPTED: "bg-success/10 text-success",
  REVOKED: "bg-danger/10 text-danger",
};

export default function HealerRegistrationPage() {
  const { user } = useAuth();
  const isSupadm = user?.role === "SUPADM";

  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useAdminInvites(page);
  const { data: clinics } = useAdminClinics();

  const createInvite = useCreateInvite();
  const resendInvite = useResendInvite();
  const revokeInvite = useRevokeInvite();

  const [email, setEmail] = useState("");
  const [role, setRole] = useState("HEALER");
  const [clinicId, setClinicId] = useState("");

  const [confirmingRevokeId, setConfirmingRevokeId] = useState<string | null>(null);

  const roleOptions = isSupadm ? ROLE_OPTIONS : ROLE_OPTIONS.filter((r) => r !== "SUPADM");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    createInvite.mutate(
      {
        email,
        role,
        ...(isSupadm && clinicId ? { clinicId } : {}),
      },
      {
        onSuccess: () => {
          setEmail("");
          setRole("HEALER");
          setClinicId("");
        },
      }
    );
  };

  const Pagination = () =>
    data ? (
      <div className="flex items-center justify-between mt-4 text-sm text-text-light">
        <span>{data.totalElements} invite{data.totalElements !== 1 ? "s" : ""}</span>
        <div className="flex items-center gap-2">
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className="p-1 rounded disabled:opacity-40 hover:bg-background"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <span>{page + 1} / {data.totalPages || 1}</span>
          <button
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="p-1 rounded disabled:opacity-40 hover:bg-background"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    ) : null;

  const canShowActions = (status: AdminInvite["status"]) => status === "PENDING" || status === "EXPIRED";

  return (
    <div>
      <h1 className="text-2xl font-semibold text-text mb-6">Healer Invitations</h1>

      {/* Create-invite form card */}
      <div className="bg-card border border-border rounded-xl shadow-sm p-6 mb-8">
        <h2 className="text-xl font-semibold text-text mb-4">Send a New Invite</h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-text-light uppercase tracking-wide mb-1">
                Email
              </label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full border border-border rounded-lg px-3 py-2 text-sm text-text bg-background focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="healer@example.com"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-text-light uppercase tracking-wide mb-1">
                Role
              </label>
              <select
                value={role}
                onChange={(e) => setRole(e.target.value)}
                className="w-full border border-border rounded-lg px-3 py-2 text-sm text-text bg-background focus:outline-none focus:ring-2 focus:ring-primary"
              >
                {roleOptions.map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
              </select>
            </div>
            {isSupadm && (
              <div>
                <label className="block text-xs font-semibold text-text-light uppercase tracking-wide mb-1">
                  Clinic
                </label>
                <select
                  required
                  value={clinicId}
                  onChange={(e) => setClinicId(e.target.value)}
                  className="w-full border border-border rounded-lg px-3 py-2 text-sm text-text bg-background focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="" disabled>
                    Select a clinic
                  </option>
                  {clinics?.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>
          <button
            type="submit"
            disabled={createInvite.isPending}
            className="bg-primary hover:bg-primary-dark text-white font-semibold rounded-lg px-4 py-2.5 text-sm disabled:opacity-50 transition-colors"
          >
            {createInvite.isPending ? "Sending…" : "Send Invite"}
          </button>
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
          <div className="hidden md:block rounded-xl border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-background text-text-light uppercase text-xs font-semibold tracking-wide">
                <tr>
                  <th className="px-4 py-3 text-left">Email</th>
                  <th className="px-4 py-3 text-left">Role</th>
                  <th className="px-4 py-3 text-left">Status</th>
                  <th className="px-4 py-3 text-left">Sent</th>
                  <th className="px-4 py-3 text-left">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.content.map((invite) => (
                  <tr key={invite.id} className="hover:bg-background/50 transition-colors">
                    <td className="px-4 py-3 font-medium text-text">{invite.email}</td>
                    <td className="px-4 py-3 text-text-light">{invite.role}</td>
                    <td className="px-4 py-3">
                      <span className={cn("px-2 py-0.5 rounded-full text-xs font-semibold", STATUS_BADGE[invite.status])}>
                        {invite.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-text-light text-xs">
                      {new Date(invite.createdAt).toLocaleDateString()}
                    </td>
                    <td className="px-4 py-3">
                      {canShowActions(invite.status) &&
                        (confirmingRevokeId === invite.id ? (
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() => revokeInvite.mutate(invite.id, { onSettled: () => setConfirmingRevokeId(null) })}
                              disabled={revokeInvite.isPending}
                              className="px-3 py-1.5 rounded-lg bg-danger text-white text-xs font-medium disabled:opacity-50 transition-colors"
                            >
                              Confirm Revoke
                            </button>
                            <button
                              type="button"
                              onClick={() => setConfirmingRevokeId(null)}
                              disabled={revokeInvite.isPending}
                              className="px-3 py-1.5 text-xs font-medium text-text-light disabled:opacity-50 transition-colors"
                            >
                              Keep Invite
                            </button>
                          </div>
                        ) : (
                          <div className="flex items-center gap-3">
                            <button
                              type="button"
                              onClick={() => resendInvite.mutate({ id: invite.id, email: invite.email })}
                              disabled={resendInvite.isPending}
                              className="text-xs font-medium text-primary hover:underline disabled:opacity-50 transition-colors"
                            >
                              Resend
                            </button>
                            <button
                              type="button"
                              onClick={() => setConfirmingRevokeId(invite.id)}
                              className="text-xs font-medium text-danger hover:underline transition-colors"
                            >
                              Revoke
                            </button>
                          </div>
                        ))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-3">
            {data.content.map((invite) => (
              <div key={invite.id} className="border border-border rounded-xl p-4">
                <div className="flex items-start justify-between mb-2">
                  <span className="font-semibold text-text text-sm truncate">{invite.email}</span>
                  <span className={cn("px-2 py-0.5 rounded-full text-xs font-semibold shrink-0", STATUS_BADGE[invite.status])}>
                    {invite.status}
                  </span>
                </div>
                <p className="text-xs text-text-light">{invite.role}</p>
                <p className="text-xs text-text-light mt-1">{new Date(invite.createdAt).toLocaleDateString()}</p>
                {canShowActions(invite.status) && (
                  <div className="mt-3">
                    {confirmingRevokeId === invite.id ? (
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => revokeInvite.mutate(invite.id, { onSettled: () => setConfirmingRevokeId(null) })}
                          disabled={revokeInvite.isPending}
                          className="px-3 py-1.5 rounded-lg bg-danger text-white text-xs font-medium disabled:opacity-50 transition-colors"
                        >
                          Confirm Revoke
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmingRevokeId(null)}
                          disabled={revokeInvite.isPending}
                          className="px-3 py-1.5 text-xs font-medium text-text-light disabled:opacity-50 transition-colors"
                        >
                          Keep Invite
                        </button>
                      </div>
                    ) : (
                      <div className="flex items-center gap-3">
                        <button
                          type="button"
                          onClick={() => resendInvite.mutate({ id: invite.id, email: invite.email })}
                          disabled={resendInvite.isPending}
                          className="text-xs font-medium text-primary hover:underline disabled:opacity-50 transition-colors"
                        >
                          Resend
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmingRevokeId(invite.id)}
                          className="text-xs font-medium text-danger hover:underline transition-colors"
                        >
                          Revoke
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>

          <Pagination />
        </>
      )}
    </div>
  );
}
