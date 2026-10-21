import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import { useAdminAccounts } from "../../hooks/admin/useAdminAccounts";
import { endsSoon, relativeFromNow, shortDate } from "../lib/format";

const FILTERS = [
  { value: "", label: "All" },
  { value: "HEALER", label: "Healer" },
  { value: "CLINIC", label: "Clinic" },
];
const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

/** New-view Accounts: same data and navigation as the classic page, laid out as in the approved mockup. */
export default function NewAccountsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [ownerType, setOwnerType] = useState("");
  const { data, isLoading, isError } = useAdminAccounts(ownerType || undefined, page);
  const open = (type: string, id: string) => navigate(`/admin/accounts/${type}/${id}/transactions`);
  const from = data && data.totalElements ? page * data.size + 1 : 0;
  const to = data ? page * data.size + data.content.length : 0;

  return (
    <div className="hlx">
      <div className="page-head">
        <div>
          <h1>Credit accounts</h1>
          <p className="muted" style={{ marginTop: 6 }}>Credit balances and plans for every healer and clinic.</p>
        </div>
        <div className="seg" role="group" aria-label="Account type">
          {FILTERS.map((f) => (
            <button
              key={f.label}
              type="button"
              className={ownerType === f.value ? "on" : ""}
              aria-pressed={ownerType === f.value}
              onClick={() => {
                setOwnerType(f.value);
                setPage(0);
              }}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {isLoading && <p className="muted">Loading accounts…</p>}
      {isError && <p className="err">Failed to load accounts.</p>}

      {data && (
        <>
          <div className="surface t-wrap">
            <table className="t">
              <thead>
                <tr>
                  <th>Owner</th>
                  <th className="r">Free</th>
                  <th className="r">Paid</th>
                  <th>Plan</th>
                  <th>Plan ends</th>
                  <th>PAYG</th>
                  <th>Created</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content.length === 0 && (
                  <tr>
                    <td colSpan={8} className="muted" style={{ textAlign: "center" }}>No accounts found.</td>
                  </tr>
                )}
                {data.content.map((a) => (
                  <tr
                    key={a.id}
                    tabIndex={0}
                    style={{ cursor: "pointer" }}
                    onClick={() => open(a.owner_type, a.owner_id)}
                    onKeyDown={(e) => e.key === "Enter" && open(a.owner_type, a.owner_id)}
                  >
                    <td data-label="Owner">
                      {a.owner_name ? (
                        <div className="name">{a.owner_name}</div>
                      ) : (
                        <div className="name small" style={{ fontFamily: "ui-monospace, monospace" }}>{a.owner_id.slice(0, 13)}…</div>
                      )}
                      <div className="small muted">{titleCase(a.owner_type)}</div>
                    </td>
                    <td className="r" data-label="Free"><span className="num">{a.free_balance}</span></td>
                    <td className="r" data-label="Paid"><span className="num">{a.paid_balance}</span></td>
                    <td data-label="Plan"><span className={`badge${a.plan_tier === "FREE" ? " off" : ""}`}>{a.plan_tier}</span></td>
                    <td data-label="Plan ends">
                      {a.plan_expires_at ? (
                        <div>
                          <div className="num">{shortDate(a.plan_expires_at)}</div>
                          <div className={`small ${endsSoon(a.plan_expires_at) ? "warn-text" : "muted"}`}>{relativeFromNow(a.plan_expires_at)}</div>
                        </div>
                      ) : (
                        <div>
                          <div className="num">No plan end</div>
                          <div className="small muted">{a.plan_tier === "FREE" ? "Free plan" : ""}</div>
                        </div>
                      )}
                    </td>
                    <td data-label="PAYG"><span className="muted">{a.payg_enabled ? "On" : "Off"}</span></td>
                    <td data-label="Created"><span className="muted num">{shortDate(a.created_at)}</span></td>
                    <td className="r" data-label="">
                      <ChevronRight className="ico" aria-hidden="true" style={{ color: "var(--muted)" }} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 4px" }}>
            <span className="small muted num">
              Showing {from}-{to} of {data.totalElements}
            </span>
            <div style={{ display: "flex", gap: 8 }}>
              <button className="btn btn-secondary" type="button" style={{ height: 36 }} disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Previous
              </button>
              <button className="btn btn-secondary" type="button" style={{ height: 36 }} disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Next
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
