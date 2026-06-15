import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAdminAccounts } from "../../hooks/admin/useAdminAccounts";
import { ChevronRight, ChevronLeft } from "lucide-react";
import { cn } from "../../lib/utils";

const TIER_COLORS: Record<string, string> = {
  FREE: "bg-gray-100 text-gray-700",
  STARTER: "bg-blue-100 text-blue-700",
  PRO: "bg-purple-100 text-purple-700",
  ENTERPRISE: "bg-yellow-100 text-yellow-700",
};

export default function AccountsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [ownerTypeFilter, setOwnerTypeFilter] = useState<string>("");
  const { data, isLoading, isError } = useAdminAccounts(ownerTypeFilter || undefined, page);

  const goToTx = (ownerType: string, ownerId: string) =>
    navigate(`/admin/accounts/${ownerType}/${ownerId}/transactions`);

  const Pagination = () =>
    data ? (
      <div className="flex items-center justify-between mt-4 text-sm text-text-light">
        <span>{data.totalElements} account{data.totalElements !== 1 ? "s" : ""}</span>
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
    ) : null;

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-xl md:text-2xl font-bold text-text">Credit Accounts</h1>
        <select
          className="border border-border rounded-lg px-3 py-2 text-sm text-text bg-surface"
          value={ownerTypeFilter}
          onChange={(e) => { setOwnerTypeFilter(e.target.value); setPage(0); }}
        >
          <option value="">All Types</option>
          <option value="CLINIC">Clinic</option>
          <option value="HEALER">Healer</option>
        </select>
      </div>

      {isLoading && <p className="text-text-light">Loading accounts…</p>}
      {isError && <p className="text-red-500">Failed to load accounts.</p>}

      {data && (
        <>
          {/* Desktop table */}
          <div className="hidden md:block rounded-xl border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-surface text-text-light uppercase text-xs tracking-wide">
                <tr>
                  <th className="px-4 py-3 text-left">Type</th>
                  <th className="px-4 py-3 text-left">Owner ID</th>
                  <th className="px-4 py-3 text-right">Free</th>
                  <th className="px-4 py-3 text-right">Paid</th>
                  <th className="px-4 py-3 text-left">Tier</th>
                  <th className="px-4 py-3 text-left">PAYG</th>
                  <th className="px-4 py-3 text-left">Created</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.content.map((acc) => (
                  <tr key={acc.id} onClick={() => goToTx(acc.owner_type, acc.owner_id)}
                    className="hover:bg-surface/50 cursor-pointer transition-colors">
                    <td className="px-4 py-3 font-medium text-text">{acc.owner_type}</td>
                    <td className="px-4 py-3 font-mono text-xs text-text-light truncate max-w-[160px]">{acc.owner_id}</td>
                    <td className="px-4 py-3 text-right">{acc.free_balance}</td>
                    <td className="px-4 py-3 text-right">{acc.paid_balance}</td>
                    <td className="px-4 py-3">
                      <span className={cn("px-2 py-0.5 rounded-full text-xs font-semibold", TIER_COLORS[acc.plan_tier] ?? "bg-gray-100 text-gray-700")}>
                        {acc.plan_tier}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-text-light">{acc.payg_enabled ? "On" : "Off"}</td>
                    <td className="px-4 py-3 text-text-light text-xs">{new Date(acc.created_at).toLocaleDateString()}</td>
                    <td className="px-4 py-3 text-text-light"><ChevronRight className="w-4 h-4" /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-3">
            {data.content.map((acc) => (
              <div key={acc.id} onClick={() => goToTx(acc.owner_type, acc.owner_id)}
                className="border border-border rounded-xl p-4 cursor-pointer active:bg-surface/60 transition-colors">
                <div className="flex items-start justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-text text-sm">{acc.owner_type}</span>
                    <span className={cn("px-2 py-0.5 rounded-full text-xs font-semibold", TIER_COLORS[acc.plan_tier] ?? "bg-gray-100 text-gray-700")}>
                      {acc.plan_tier}
                    </span>
                  </div>
                  <ChevronRight className="w-4 h-4 text-text-light shrink-0" />
                </div>
                <p className="font-mono text-xs text-text-light truncate mb-3">{acc.owner_id}</p>
                <div className="grid grid-cols-3 gap-2 text-xs">
                  <div className="bg-surface rounded-lg px-2 py-1.5 text-center">
                    <div className="text-text-light">Free</div>
                    <div className="font-semibold text-text">{acc.free_balance}</div>
                  </div>
                  <div className="bg-surface rounded-lg px-2 py-1.5 text-center">
                    <div className="text-text-light">Paid</div>
                    <div className="font-semibold text-text">{acc.paid_balance}</div>
                  </div>
                  <div className="bg-surface rounded-lg px-2 py-1.5 text-center">
                    <div className="text-text-light">PAYG</div>
                    <div className="font-semibold text-text">{acc.payg_enabled ? "On" : "Off"}</div>
                  </div>
                </div>
                <p className="text-xs text-text-light mt-2">{new Date(acc.created_at).toLocaleDateString()}</p>
              </div>
            ))}
          </div>

          <Pagination />
        </>
      )}
    </div>
  );
}
