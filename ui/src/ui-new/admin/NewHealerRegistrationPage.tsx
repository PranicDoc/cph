import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import { UserPlus } from "lucide-react";
import { useAuth } from "../../context/AuthContext";
import { useCan } from "../../hooks/useMyPermissions";
import { useAdminInvites, useCreateInvite, useResendInvite, useRevokeInvite, type AdminInvite } from "../../hooks/admin/useAdminInvites";
import { useAdminHealers, useRemoveHealer, useSetHealerActive, type AdminHealer } from "../../hooks/admin/useAdminHealers";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { HlField } from "../components/Controls";
import { shortDate } from "../lib/format";

const FILTERS = [
  { key: "ALL", label: "All" },
  { key: "PENDING", label: "Pending" },
  { key: "ACCEPTED", label: "Accepted" },
  { key: "EXPIRED", label: "Expired" },
] as const;
const TONE: Record<AdminInvite["status"], string> = { PENDING: "warn", ACCEPTED: "ok", EXPIRED: "off", REVOKED: "off" };
const label = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

/** New-view Healer invites: same invite, resend, revoke and healer-management rules as the classic page. */
export default function NewHealerRegistrationPage() {
  const { user } = useAuth();
  const can = useCan();
  const canInvite = can("HEALER_REG", "CREATE");
  const canManageInvites = can("HEALER_REG", "UPDATE");
  const canSeeHealers = can("ACCOUNTS", "READ");
  const canSetHealerActive = can("ACCOUNTS", "UPDATE");
  const canRemoveHealer = can("ACCOUNTS", "DELETE");
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<(typeof FILTERS)[number]["key"]>("ALL");
  const { data, isLoading, isError } = useAdminInvites(page);
  const createInvite = useCreateInvite();
  const resendInvite = useResendInvite();
  const revokeInvite = useRevokeInvite();
  const [revokeTarget, setRevokeTarget] = useState<AdminInvite | null>(null);
  const { data: healers, isLoading: healersLoading } = useAdminHealers(canSeeHealers);
  const setHealerActive = useSetHealerActive();
  const removeHealer = useRemoveHealer();
  const [removeTarget, setRemoveTarget] = useState<AdminHealer | null>(null);

  const inviteForm = useForm({
    defaultValues: { email: "" },
    onSubmit: async ({ value, formApi }) => {
      createInvite.mutate({ email: value.email }, { onSuccess: () => formApi.reset() });
    },
  });

  const invites = (data?.content ?? []).filter((i) => filter === "ALL" || i.status === filter);
  const from = data && data.totalElements ? page * data.size + 1 : 0;
  const to = data ? page * data.size + data.content.length : 0;

  return (
    <div className="hlx">
      <div className="page-head">
        <div>
          <h1>Healer invites</h1>
          <p className="muted" style={{ marginTop: 6 }}>Invite healers or staff by email. They get a secure link to set up their account.</p>
        </div>
        <div className="seg" role="group" aria-label="Invite status">
          {FILTERS.map((f) => (
            <button key={f.key} type="button" className={filter === f.key ? "on" : ""} aria-pressed={filter === f.key} onClick={() => setFilter(f.key)}>
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {canInvite && <form
        className="surface card"
        style={{ display: "flex", alignItems: "flex-end", gap: 14, flexWrap: "wrap" }}
        onSubmit={(e) => {
          e.preventDefault();
          inviteForm.handleSubmit();
        }}
      >
        <div style={{ flex: "1 1 280px", maxWidth: 420 }}>
          <inviteForm.Field name="email">
            {(f) => (
              <HlField label="Send a new invite" htmlFor="invite-email">
                <input id="invite-email" type="email" className="input" placeholder="healer@example.com" value={f.state.value} onChange={(e) => f.handleChange(e.target.value)} onBlur={f.handleBlur} />
              </HlField>
            )}
          </inviteForm.Field>
        </div>
        <button className="btn btn-primary" type="submit" disabled={createInvite.isPending}>
          <UserPlus className="ico" /> {createInvite.isPending ? "Sending…" : "Send invite"}
        </button>
      </form>}

      {isLoading && <p className="muted">Loading invites…</p>}
      {isError && (
        <div className="surface card" style={{ textAlign: "center" }}>
          <h2>Couldn't load invites</h2>
          <p className="muted" style={{ marginTop: 6 }}>Something went wrong while loading your invite list. Please refresh the page or try again in a moment.</p>
        </div>
      )}
      {data && data.content.length === 0 && page === 0 && (
        <div className="surface card" style={{ textAlign: "center", padding: 40 }}>
          <h2>No invites sent yet</h2>
          <p className="muted" style={{ marginTop: 6 }}>Invite a healer or staff member by email to get started. They'll receive a secure link to set up their account.</p>
        </div>
      )}

      {data && data.content.length > 0 && (
        <>
          <div className="surface t-wrap">
            <table className="t t-actions-last">
              <thead>
                <tr>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Sent</th>
                  <th>Expires</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {invites.length === 0 && (
                  <tr><td colSpan={6} className="muted" style={{ textAlign: "center" }}>No {filter.toLowerCase()} invites on this page.</td></tr>
                )}
                {invites.map((i) => (
                  <tr key={i.id}>
                    <td data-label="Email"><div className="name">{i.email}</div></td>
                    <td data-label="Role"><span className="muted">{i.role}</span></td>
                    <td data-label="Sent"><span className="num">{shortDate(i.createdAt)}</span></td>
                    <td data-label="Expires"><span className="num muted">{i.status === "ACCEPTED" ? "-" : shortDate(i.expiresAt)}</span></td>
                    <td data-label="Status"><span className={`badge ${TONE[i.status]}`}>{label(i.status)}</span></td>
                    <td className="r" data-label="">
                      {canManageInvites && (i.status === "PENDING" || i.status === "EXPIRED") && (
                        <div style={{ display: "flex", gap: 2, justifyContent: "flex-end" }}>
                          <button className="btn btn-ghost" type="button" style={{ height: 34 }} disabled={resendInvite.isPending} onClick={() => resendInvite.mutate({ id: i.id, email: i.email })}>
                            Resend
                          </button>
                          <button className="btn btn-danger" type="button" style={{ height: 34 }} onClick={() => setRevokeTarget(i)}>
                            Revoke
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 4px" }}>
            <span className="small num ongr">Showing {from}-{to} of {data.totalElements}</span>
            <div style={{ display: "flex", gap: 8 }}>
              <button className="btn btn-secondary" type="button" style={{ height: 36 }} disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</button>
              <button className="btn btn-secondary" type="button" style={{ height: 36 }} disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>Next</button>
            </div>
          </div>
        </>
      )}

      {canSeeHealers && (
        <>
          <div className="page-head" style={{ marginTop: 12 }}>
            <div>
              <h2 style={{ color: "inherit" }}>Healers</h2>
              <p className="muted" style={{ marginTop: 6 }}>Activate, deactivate or remove existing accounts.</p>
            </div>
          </div>
          {healersLoading && <p className="muted">Loading healers…</p>}
          {healers && healers.length > 0 && (
            <div className="surface t-wrap">
              <table className="t t-actions-last">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Role</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {healers.map((h) => (
                    <tr key={h.id}>
                      <td data-label="Name"><div className="name">{h.fullName}</div></td>
                      <td data-label="Email"><span className="muted">{h.email}</span></td>
                      <td data-label="Role"><span className="muted">{h.role}</span></td>
                      <td data-label="Status"><span className={`badge ${h.active ? "ok" : "off"}`}>{h.active ? "Active" : "Inactive"}</span></td>
                      <td className="r" data-label="">
                        {h.id === user?.id ? (
                          <span className="small muted">You</span>
                        ) : (
                          <div style={{ display: "flex", gap: 2, justifyContent: "flex-end" }}>
                            {canSetHealerActive && (
                              <button className="btn btn-ghost" type="button" style={{ height: 34 }} disabled={setHealerActive.isPending} onClick={() => setHealerActive.mutate({ id: h.id, active: !h.active })}>
                                {h.active ? "Deactivate" : "Activate"}
                              </button>
                            )}
                            {canRemoveHealer && (
                              <button className="btn btn-danger" type="button" style={{ height: 34 }} onClick={() => setRemoveTarget(h)}>
                                Remove
                              </button>
                            )}
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      <ConfirmDialog
        open={removeTarget !== null}
        onOpenChange={(open) => !open && setRemoveTarget(null)}
        title="Remove healer?"
        description={removeTarget ? `${removeTarget.fullName} will no longer be able to sign in. Their patients, cases, and PDFs are kept as-is.` : undefined}
        confirmLabel="Confirm Remove"
        cancelLabel="Cancel"
        destructive
        loading={removeHealer.isPending}
        onConfirm={() => {
          if (removeTarget) removeHealer.mutate(removeTarget.id, { onSettled: () => setRemoveTarget(null) });
        }}
      />
      <ConfirmDialog
        open={revokeTarget !== null}
        onOpenChange={(open) => !open && setRevokeTarget(null)}
        title="Revoke invite?"
        description={revokeTarget ? `The invite sent to ${revokeTarget.email} will stop working immediately.` : undefined}
        confirmLabel="Confirm Revoke"
        cancelLabel="Keep Invite"
        destructive
        loading={revokeInvite.isPending}
        onConfirm={() => {
          if (revokeTarget) revokeInvite.mutate(revokeTarget.id, { onSettled: () => setRevokeTarget(null) });
        }}
      />
    </div>
  );
}
