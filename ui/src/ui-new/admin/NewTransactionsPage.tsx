import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { useAdminTransactions } from "../../hooks/admin/useAdminTransactions";
import { shortDate } from "../lib/format";

type Tx = { id: string; type: string; amount: number; balance_after: number; notes?: string | null; created_at: string };

const FILTERS = [
  { key: "all", label: "All" },
  { key: "credits", label: "Credits" },
  { key: "debits", label: "Debits" },
] as const;

const TONE: Record<string, string> = { PURCHASE: "ok", GRANT: "warn", REFUND: "ok", TOPUP_AUTO: "", ADJUSTMENT: "", DEDUCT: "off", MONTHLY_RESET: "off" };
const label = (t: string) => t.charAt(0) + t.slice(1).toLowerCase().replace(/_/g, " ");

/** New-view Transactions: same data as the classic page; the All / Credits / Debits filter applies to the page on screen. */
export default function NewTransactionsPage() {
  const { ownerType, ownerId } = useParams<{ ownerType: string; ownerId: string }>();
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<(typeof FILTERS)[number]["key"]>("all");
  const { data, isLoading, isError } = useAdminTransactions(ownerType ?? "", ownerId ?? "", page);
  const rows: Tx[] = (data?.content ?? []).filter((t: Tx) => (filter === "credits" ? t.amount >= 0 : filter === "debits" ? t.amount < 0 : true));
  const from = data && data.totalElements ? page * data.size + 1 : 0;
  const to = data ? page * data.size + data.content.length : 0;

  return (
    <div className="hlx">
      <div className="page-head">
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <Link to="/admin/accounts" className="icon-btn" aria-label="Back to accounts">
            <ArrowLeft className="ico" />
          </Link>
          <div>
            <h1>Transactions</h1>
            <p className="muted" style={{ marginTop: 6, wordBreak: "break-all" }}>
              {ownerType} · {ownerId}
            </p>
          </div>
        </div>
        <div className="seg" role="group" aria-label="Filter">
          {FILTERS.map((f) => (
            <button key={f.key} type="button" className={filter === f.key ? "on" : ""} aria-pressed={filter === f.key} onClick={() => setFilter(f.key)}>
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {isLoading && <p className="muted">Loading…</p>}
      {isError && <p className="err">Failed to load transactions.</p>}

      {data && (
        <>
          <div className="surface t-wrap">
            <table className="t">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Type</th>
                  <th>Description</th>
                  <th className="r">Credits</th>
                  <th className="r">Balance after</th>
                </tr>
              </thead>
              <tbody>
                {rows.length === 0 && (
                  <tr>
                    <td colSpan={5} className="muted" style={{ textAlign: "center" }}>No transactions found.</td>
                  </tr>
                )}
                {rows.map((t) => (
                  <tr key={t.id}>
                    <td data-label="Date" className="num">{shortDate(t.created_at)}</td>
                    <td data-label="Type"><span className={`badge ${TONE[t.type] ?? ""}`}>{label(t.type)}</span></td>
                    <td data-label="Description">{t.notes ?? "—"}</td>
                    <td className="r" data-label="Credits">
                      <span className="num" style={{ fontWeight: 600, color: t.amount >= 0 ? "var(--success)" : undefined }}>
                        {t.amount >= 0 ? "+" : ""}
                        {t.amount}
                      </span>
                    </td>
                    <td className="r" data-label="Balance after"><span className="num">{t.balance_after}</span></td>
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
