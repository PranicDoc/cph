import { useState } from "react";
import { useMyCreditTransactions, transactionLabel } from "../../hooks/useMyCreditTransactions";
import { shortDate } from "../lib/format";

/** New-view credit history on the Profile page: the healer's own transactions, ten at a time. */
export function CreditHistoryCard() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useMyCreditTransactions(page);

  return (
    <section className="surface card">
      <div className="card-h">
        <h2>Credit history</h2>
      </div>
      {isLoading && <p className="muted">Loading…</p>}
      {isError && <p className="err">Couldn't load your credit history.</p>}
      {data && data.content.length === 0 && <p className="muted">No credit activity yet.</p>}
      {data && data.content.length > 0 && (
        <>
          {data.content.map((t) => (
            <div
              key={t.id}
              style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, padding: "10px 0", borderBottom: "1px solid var(--hair)" }}
            >
              <div style={{ minWidth: 0 }}>
                <div style={{ fontWeight: 600 }}>{transactionLabel(t.type)}</div>
                <div className="small muted" style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                  <span className="num">{shortDate(t.created_at)}</span>
                  {t.notes ? ` · ${t.notes}` : ""}
                </div>
              </div>
              <div style={{ textAlign: "right", flexShrink: 0 }}>
                <div className="num" style={{ fontWeight: 600, color: t.amount >= 0 ? "var(--success)" : undefined }}>
                  {t.amount >= 0 ? "+" : ""}
                  {t.amount}
                </div>
                <div className="small muted num">Balance {t.balance_after}</div>
              </div>
            </div>
          ))}
          {data.totalPages > 1 && (
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 12 }}>
              <button className="btn btn-secondary" type="button" style={{ height: 34 }} disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Newer
              </button>
              <button className="btn btn-secondary" type="button" style={{ height: 34 }} disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Older
              </button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
