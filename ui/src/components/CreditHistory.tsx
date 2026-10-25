import { useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useMyCreditTransactions, transactionLabel } from "../hooks/useMyCreditTransactions";

/** Classic-view credit history on the Profile page: the healer's own transactions, ten at a time. */
export function CreditHistory() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useMyCreditTransactions(page);

  return (
    <div className="bg-card border border-border rounded-xl shadow-sm p-5">
      <h2 className="text-sm font-semibold text-primary-dark mb-1">Credit history</h2>
      <p className="text-xs text-text-light mb-4">Credits you received and spent.</p>

      {isLoading && (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full rounded-lg" />)}
        </div>
      )}
      {isError && <p className="text-sm text-red-500">Couldn't load your credit history.</p>}
      {data && data.content.length === 0 && (
        <p className="text-sm text-text-light">No credit activity yet.</p>
      )}

      {data && data.content.length > 0 && (
        <>
          <ul className="divide-y divide-border">
            {data.content.map((t) => (
              <li key={t.id} className="flex items-center justify-between gap-3 py-2.5">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-text">{transactionLabel(t.type)}</p>
                  <p className="text-xs text-text-light truncate">
                    {new Date(t.created_at).toLocaleDateString()}
                    {t.notes ? ` · ${t.notes}` : ""}
                  </p>
                </div>
                <div className="text-right shrink-0">
                  <p className={`text-sm font-semibold ${t.amount >= 0 ? "text-success" : "text-text"}`}>
                    {t.amount >= 0 ? "+" : ""}
                    {t.amount}
                  </p>
                  <p className="text-xs text-text-light">Balance {t.balance_after}</p>
                </div>
              </li>
            ))}
          </ul>
          {data.totalPages > 1 && (
            <div className="mt-3 flex items-center justify-end gap-2 text-xs text-text-light">
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="Newer"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                <ChevronLeft className="w-4 h-4" />
              </Button>
              <span>
                {page + 1} / {data.totalPages}
              </span>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="Older"
                disabled={page + 1 >= data.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                <ChevronRight className="w-4 h-4" />
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
