import { useState } from "react";
import { useParams, Link } from "react-router-dom";
import { useAdminTransactions } from "../../hooks/admin/useAdminTransactions";
import { ChevronLeft, ChevronRight, ArrowLeft } from "lucide-react";
import { cn } from "../../lib/utils";

const TYPE_COLORS: Record<string, string> = {
  DEDUCT: "bg-red-100 text-red-700",
  PURCHASE: "bg-green-100 text-green-700",
  GRANT: "bg-blue-100 text-blue-700",
  TOPUP_AUTO: "bg-purple-100 text-purple-700",
  REFUND: "bg-yellow-100 text-yellow-700",
  ADJUSTMENT: "bg-orange-100 text-orange-700",
  MONTHLY_RESET: "bg-indigo-100 text-indigo-700",
};

export default function TransactionsPage() {
  const { ownerType, ownerId } = useParams<{ ownerType: string; ownerId: string }>();
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useAdminTransactions(ownerType ?? "", ownerId ?? "", page);

  return (
    <div>
      <div className="flex items-start gap-3 mb-4">
        <Link to="/admin/accounts" className="mt-1 p-1.5 rounded-lg hover:bg-surface text-text-light shrink-0">
          <ArrowLeft className="w-4 h-4" />
        </Link>
        <div className="min-w-0">
          <h1 className="text-xl md:text-2xl font-bold text-text">Transactions</h1>
          <p className="text-xs text-text-light font-mono truncate">{ownerType} / {ownerId}</p>
        </div>
      </div>

      {isLoading && <p className="text-text-light">Loading…</p>}
      {isError && <p className="text-red-500">Failed to load transactions.</p>}

      {data && (
        <>
          {/* Desktop table */}
          <div className="hidden md:block rounded-xl border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-surface text-text-light uppercase text-xs tracking-wide">
                <tr>
                  <th className="px-4 py-3 text-left">Type</th>
                  <th className="px-4 py-3 text-right">Amount</th>
                  <th className="px-4 py-3 text-right">Balance After</th>
                  <th className="px-4 py-3 text-left">Notes</th>
                  <th className="px-4 py-3 text-left">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.content.map((tx) => (
                  <tr key={tx.id} className="hover:bg-surface/50">
                    <td className="px-4 py-3">
                      <span className={cn("px-2 py-0.5 rounded-full text-xs font-semibold", TYPE_COLORS[tx.type] ?? "bg-gray-100 text-gray-700")}>
                        {tx.type}
                      </span>
                    </td>
                    <td className={cn("px-4 py-3 text-right font-mono font-semibold", tx.amount >= 0 ? "text-green-600" : "text-red-600")}>
                      {tx.amount >= 0 ? "+" : ""}{tx.amount}
                    </td>
                    <td className="px-4 py-3 text-right font-mono">{tx.balance_after}</td>
                    <td className="px-4 py-3 text-text-light text-xs max-w-[200px] truncate">{tx.notes ?? "—"}</td>
                    <td className="px-4 py-3 text-text-light text-xs">{new Date(tx.created_at).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-2">
            {data.content.map((tx) => (
              <div key={tx.id} className="border border-border rounded-xl p-3">
                <div className="flex items-center justify-between mb-1">
                  <span className={cn("px-2 py-0.5 rounded-full text-xs font-semibold", TYPE_COLORS[tx.type] ?? "bg-gray-100 text-gray-700")}>
                    {tx.type}
                  </span>
                  <div className="flex items-center gap-3 text-sm">
                    <span className={cn("font-mono font-semibold", tx.amount >= 0 ? "text-green-600" : "text-red-600")}>
                      {tx.amount >= 0 ? "+" : ""}{tx.amount}
                    </span>
                    <span className="text-text-light font-mono text-xs">→ {tx.balance_after}</span>
                  </div>
                </div>
                {tx.notes && <p className="text-xs text-text-light truncate">{tx.notes}</p>}
                <p className="text-xs text-text-light mt-1">{new Date(tx.created_at).toLocaleString()}</p>
              </div>
            ))}
          </div>

          <div className="flex items-center justify-between mt-4 text-sm text-text-light">
            <span>{data.totalElements} transaction{data.totalElements !== 1 ? "s" : ""}</span>
            <div className="flex items-center gap-2">
              <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}
                className="p-1 rounded disabled:opacity-40 hover:bg-surface">
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span>{page + 1} / {data.totalPages || 1}</span>
              <button disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}
                className="p-1 rounded disabled:opacity-40 hover:bg-surface">
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
