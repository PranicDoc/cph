import { useState } from "react";
import { useParams, Link } from "react-router-dom";
import type { ColumnDef } from "@tanstack/react-table";
import { useAdminTransactions } from "../../hooks/admin/useAdminTransactions";
import { ChevronLeft, ChevronRight, ArrowLeft } from "lucide-react";
import { cn } from "../../lib/utils";
import { DataTable } from "@/components/ui/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

const TYPE_COLORS: Record<string, string> = {
  DEDUCT: "bg-red-100 text-red-700",
  PURCHASE: "bg-green-100 text-green-700",
  GRANT: "bg-blue-100 text-blue-700",
  TOPUP_AUTO: "bg-purple-100 text-purple-700",
  REFUND: "bg-yellow-100 text-yellow-700",
  ADJUSTMENT: "bg-orange-100 text-orange-700",
  MONTHLY_RESET: "bg-indigo-100 text-indigo-700",
};

type TransactionRow = {
  id: string;
  type: string;
  amount: number;
  balance_after: number;
  notes?: string | null;
  created_at: string;
};

function TypeBadge({ type }: { type: string }) {
  return (
    <Badge
      className={cn(
        "font-semibold px-2",
        TYPE_COLORS[type] ?? "bg-gray-100 text-gray-700"
      )}
    >
      {type}
    </Badge>
  );
}

const transactionColumns: ColumnDef<TransactionRow, unknown>[] = [
  {
    accessorKey: "type",
    header: "Type",
    cell: (info) => <TypeBadge type={String(info.getValue())} />,
  },
  {
    accessorKey: "amount",
    header: () => <div className="text-right">Amount</div>,
    cell: (info) => {
      const amount = Number(info.getValue());
      return (
        <div
          className={cn(
            "text-right font-mono font-semibold",
            amount >= 0 ? "text-green-600" : "text-red-600"
          )}
        >
          {amount >= 0 ? "+" : ""}
          {amount}
        </div>
      );
    },
  },
  {
    accessorKey: "balance_after",
    header: () => <div className="text-right">Balance After</div>,
    cell: (info) => (
      <div className="text-right font-mono">{Number(info.getValue())}</div>
    ),
  },
  {
    accessorKey: "notes",
    header: "Notes",
    cell: (info) => (
      <span className="text-text-light text-xs max-w-[200px] truncate block">
        {(info.getValue() as string | null) ?? "—"}
      </span>
    ),
  },
  {
    accessorKey: "created_at",
    header: "Date",
    cell: (info) => (
      <span className="text-text-light text-xs">
        {new Date(String(info.getValue())).toLocaleString()}
      </span>
    ),
  },
];

export default function TransactionsPage() {
  const { ownerType, ownerId } = useParams<{ ownerType: string; ownerId: string }>();
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useAdminTransactions(ownerType ?? "", ownerId ?? "", page);

  return (
    <div>
      <div className="flex items-start gap-3 mb-4">
        <Link to="/admin/accounts" className="mt-1 p-1.5 rounded-lg hover:bg-muted text-text-light shrink-0">
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
          <div className="hidden md:block">
            <DataTable<TransactionRow>
              columns={transactionColumns}
              data={data.content}
              enableSorting={false}
              emptyMessage="No transactions found."
            />
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-2">
            {data.content.map((tx: TransactionRow) => (
              <div key={tx.id} className="border border-border rounded-xl p-3">
                <div className="flex items-center justify-between mb-1">
                  <TypeBadge type={tx.type} />
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
              <Button
                variant="ghost"
                size="icon-sm"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
                className="h-auto w-auto p-1 disabled:opacity-40"
              >
                <ChevronLeft className="w-4 h-4" />
              </Button>
              <span>{page + 1} / {data.totalPages || 1}</span>
              <Button
                variant="ghost"
                size="icon-sm"
                disabled={page + 1 >= data.totalPages}
                onClick={() => setPage((p) => p + 1)}
                className="h-auto w-auto p-1 disabled:opacity-40"
              >
                <ChevronRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
