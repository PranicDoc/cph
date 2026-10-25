import type { Dispatch, ElementType, SetStateAction } from "react";
import { Activity, Mail, Pencil, Phone, User } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { useCreditAccount } from "../../hooks/useCreditAccount";
import { planEndLine } from "../lib/planEnd";
import { CreditHistoryCard } from "../components/CreditHistoryCard";

const GOOGLE_G_ICON = "https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg";

type Draft = { fullName: string; email: string; phone: string };
type PwDraft = { current: string; newPwd: string; confirm: string };

/** Everything the classic Profile page owns; the new view only lays it out differently. */
export interface ProfilePageState {
  isLoading: boolean;
  displayName: string;
  displayEmail: string;
  displayPhone: string;
  displayLevel?: string | null;
  hasPassword: boolean;
  hasGoogleLinked: boolean;
  editing: boolean;
  draft: Draft;
  setDraft: Dispatch<SetStateAction<Draft>>;
  enterEdit: () => void;
  cancelEdit: () => void;
  saveEdit: () => void;
  savingProfile: boolean;
  pwEditing: boolean;
  setPwEditing: (on: boolean) => void;
  pwDraft: PwDraft;
  setPwDraft: Dispatch<SetStateAction<PwDraft>>;
  savePw: () => void;
  cancelPwEdit: () => void;
  savingPw: boolean;
  linkGoogle: () => void;
  linkingGoogle: boolean;
  askUnlinkGoogle: () => void;
  openUpgrade: () => void;
}

function InfoRow({ icon: Icon, label, value }: { icon: ElementType; label: string; value?: string | null }) {
  return (
    <div style={{ display: "flex", alignItems: "flex-start", gap: 12 }}>
      <div className="avatar" style={{ borderRadius: 10, width: 34, height: 34, flex: "none" }}>
        <Icon className="ico" aria-hidden="true" />
      </div>
      <div style={{ minWidth: 0 }}>
        <div className="small muted" style={{ fontWeight: 550 }}>{label}</div>
        <div style={{ marginTop: 2, overflowWrap: "anywhere" }}>{value || "—"}</div>
      </div>
    </div>
  );
}

function Field({ id, label, ...input }: { id: string; label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="lbl" htmlFor={id}>{label}</label>
      <input id={id} className="input" {...input} />
    </div>
  );
}

function RowsSkeleton({ rows }: { rows: number }) {
  return (
    <div className="stack" style={{ gap: 16 }}>
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-9 w-full rounded-lg" />
      ))}
    </div>
  );
}

export default function NewProfile({ page }: { page: ProfilePageState }) {
  const { data: account, isLoading: accountLoading } = useCreditAccount();
  const credits = account ? account.free_balance + account.paid_balance : null;

  return (
    <div className="hlx">
      <div className="page-head">
        <div>
          <h1>Profile</h1>
          <p className="muted" style={{ marginTop: 6 }}>Your details and plan.</p>
        </div>
      </div>

      <div className="grid2" style={{ alignItems: "start" }}>
        <div className="stack">
          <section className="surface card">
            <div className="card-h">
              <h2>Personal information</h2>
              {!page.editing && (
                <button type="button" className="icon-btn" aria-label="Edit personal information" onClick={page.enterEdit} disabled={page.isLoading}>
                  <Pencil className="ico" />
                </button>
              )}
            </div>
            {page.isLoading ? (
              <RowsSkeleton rows={3} />
            ) : page.editing ? (
              <div className="stack" style={{ gap: 16 }}>
                <Field id="pf-name" label="Full name" type="text" required value={page.draft.fullName}
                  onChange={(e) => page.setDraft((d) => ({ ...d, fullName: e.target.value }))} />
                <Field id="pf-email" label="Email" type="email" required value={page.draft.email}
                  onChange={(e) => page.setDraft((d) => ({ ...d, email: e.target.value }))} />
                <Field id="pf-phone" label="Phone" type="tel" placeholder="Optional" value={page.draft.phone}
                  onChange={(e) => page.setDraft((d) => ({ ...d, phone: e.target.value }))} />
                <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
                  <button type="button" className="btn btn-ghost" onClick={page.cancelEdit} disabled={page.savingProfile}>Cancel</button>
                  <button type="button" className="btn btn-primary" onClick={page.saveEdit} disabled={page.savingProfile}>
                    {page.savingProfile ? "Saving…" : "Save"}
                  </button>
                </div>
              </div>
            ) : (
              <div className="stack" style={{ gap: 20 }}>
                <InfoRow icon={User} label="Full name" value={page.displayName} />
                <InfoRow icon={Mail} label="Email" value={page.displayEmail} />
                <InfoRow icon={Phone} label="Phone" value={page.displayPhone} />
              </div>
            )}
          </section>

          <section className="surface card">
            <div className="card-h">
              <h2>{page.hasPassword ? "Change password" : "Set password"}</h2>
              {!page.pwEditing && (
                <button type="button" className="btn btn-secondary" onClick={() => page.setPwEditing(true)} disabled={page.isLoading}>
                  {page.hasPassword ? "Change password" : "Set password"}
                </button>
              )}
            </div>
            {page.pwEditing ? (
              <div className="stack" style={{ gap: 16 }}>
                {page.hasPassword && (
                  <Field id="pw-current" label="Current password" type="password" autoComplete="current-password"
                    value={page.pwDraft.current} onChange={(e) => page.setPwDraft((d) => ({ ...d, current: e.target.value }))} />
                )}
                <Field id="pw-new" label="New password" type="password" autoComplete="new-password"
                  value={page.pwDraft.newPwd} onChange={(e) => page.setPwDraft((d) => ({ ...d, newPwd: e.target.value }))} />
                <Field id="pw-confirm" label="Confirm password" type="password" autoComplete="new-password"
                  value={page.pwDraft.confirm} onChange={(e) => page.setPwDraft((d) => ({ ...d, confirm: e.target.value }))} />
                <div className="small muted">Use at least 8 characters.</div>
                <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
                  <button type="button" className="btn btn-ghost" onClick={page.cancelPwEdit} disabled={page.savingPw}>Cancel</button>
                  <button type="button" className="btn btn-primary" onClick={page.savePw} disabled={page.savingPw}>
                    {page.savingPw ? "Saving…" : page.hasPassword ? "Change password" : "Set password"}
                  </button>
                </div>
              </div>
            ) : (
              <div className="small muted">
                {page.hasPassword ? "Use at least 8 characters." : "You sign in with Google. Set a password to sign in with email too."}
              </div>
            )}
          </section>
        </div>

        <div className="stack">
          <section className="surface card">
            <div className="card-h">
              <h2>Plan and billing</h2>
              {account?.plan_tier && <span className="badge">{account.plan_tier.toUpperCase()}</span>}
            </div>
            {accountLoading ? (
              <RowsSkeleton rows={2} />
            ) : (
              <>
                <div style={{ display: "flex", alignItems: "baseline", gap: 8 }}>
                  <span className="num" style={{ fontSize: 30, fontWeight: 650 }}>{credits ?? "—"}</span>
                  <span className="muted">credits left</span>
                </div>
                <div className="small muted" style={{ margin: "6px 0 16px" }}>{planEndLine(account?.plan_expires_at)}</div>
              </>
            )}
            <button type="button" className="btn btn-primary" onClick={page.openUpgrade}>Upgrade plan</button>
          </section>

          <CreditHistoryCard />

          <section className="surface card">
            <div className="card-h">
              <h2>Google account</h2>
              {!page.isLoading && (
                page.hasGoogleLinked
                  ? <span className="badge ok"><span className="dot" />Connected</span>
                  : <span className="badge off">Not linked</span>
              )}
            </div>
            {page.isLoading ? (
              <RowsSkeleton rows={1} />
            ) : page.hasGoogleLinked ? (
              <>
                <div className="small muted">Signed in with {page.displayEmail}</div>
                <div style={{ marginTop: 12 }}>
                  <button type="button" className="btn btn-danger" style={{ padding: 0 }} onClick={page.askUnlinkGoogle} disabled={!page.hasPassword}>
                    Unlink Google account
                  </button>
                  {!page.hasPassword && <div className="help" style={{ color: "var(--danger)" }}>Set a password first</div>}
                </div>
              </>
            ) : (
              <>
                <div className="small muted">Link your Google account for faster sign-in.</div>
                <button type="button" className="btn btn-secondary" style={{ marginTop: 12 }} onClick={page.linkGoogle} disabled={page.linkingGoogle}>
                  <img src={GOOGLE_G_ICON} alt="" style={{ width: 16, height: 16 }} />
                  {page.linkingGoogle ? "Linking…" : "Link Google account"}
                </button>
              </>
            )}
          </section>

          <section className="surface card">
            <div className="card-h">
              <h2>Account details</h2>
              <span className="small muted">Read only</span>
            </div>
            {page.isLoading ? <RowsSkeleton rows={1} /> : <InfoRow icon={Activity} label="Healing level" value={page.displayLevel} />}
          </section>
        </div>
      </div>
    </div>
  );
}
