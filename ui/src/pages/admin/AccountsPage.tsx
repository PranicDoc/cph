import { useState } from "react";
import { useNavigate } from "react-router-dom";
import type { ColumnDef } from "@tanstack/react-table";
import { useAdminAccounts } from "../../hooks/admin/useAdminAccounts";
import { ChevronRight, ChevronLeft } from "lucide-react";
import { cn } from "../../lib/utils";
import { formatPlanEnd } from "../../lib/validity";
import { DataTable } from "@/components/ui/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const TIER_COLORS: Record<string, string> = {
  FREE: "bg-gray-100 text-gray-700",
  STARTER: "bg-blue-100 text-blue-700",
  PRO: "bg-purple-100 text-purple-700",
  ENTERPRISE: "bg-yellow-100 text-yellow-700",
};

type AccountRow = {
  id: string;
  owner_type: string;
  owner_id: string;
  owner_name: string | null;
  free_balance: number;
  paid_balance: number;
  plan_tier: string;
  plan_expires_at: string | null;
  payg_enabled: boolean;
  created_at: string;
};

function TierBadge({ tier }: { tier: string }) {
  return (
    <Badge
      className={cn(
        "font-semibold px-2",
        TIER_COLORS[tier] ?? "bg-gray-100 text-gray-700"
      )}
    >
      {tier}
    </Badge>
  );
}

const accountColumns: ColumnDef<AccountRow, unknown>[] = [
  {
    accessorKey: "owner_type",
    header: "Type",
    cell: (info) => (
      <span className="font-medium text-text">{String(info.getValue())}</span>
    ),
  },
  {
    accessorKey: "owner_id",
    header: "Owner",
    cell: (info) => {
      const row = info.row.original;
      return row.owner_name ? (
        <span className="font-medium text-text truncate max-w-[160px] block">
          {row.owner_name}
        </span>
      ) : (
        <span className="font-mono text-xs text-text-light truncate max-w-[160px] block">
          {String(info.getValue())}
        </span>
      );
    },
  },
  {
    accessorKey: "free_balance",
    header: () => <div className="text-right">Free</div>,
    cell: (info) => <div className="text-right">{Number(info.getValue())}</div>,
  },
  {
    accessorKey: "paid_balance",
    header: () => <div className="text-right">Paid</div>,
    cell: (info) => <div className="text-right">{Number(info.getValue())}</div>,
  },
  {
    accessorKey: "plan_tier",
    header: "Tier",
    cell: (info) => <TierBadge tier={String(info.getValue())} />,
  },
  {
    accessorKey: "plan_expires_at",
    header: "Plan ends",
    cell: (info) => (
      <span className="text-text-light text-xs">
        {formatPlanEnd(info.getValue() as string | null)}
      </span>
    ),
  },
  {
    accessorKey: "payg_enabled",
    header: "PAYG",
    cell: (info) => (
      <span className="text-text-light">{info.getValue() ? "On" : "Off"}</span>
    ),
  },
  {
    accessorKey: "created_at",
    header: "Created",
    cell: (info) => (
      <span className="text-text-light text-xs">
        {new Date(String(info.getValue())).toLocaleDateString()}
      </span>
    ),
  },
  {
    id: "chevron",
    header: "",
    cell: () => (
      <span className="text-text-light">
        <ChevronRight className="w-4 h-4" />
      </span>
    ),
  },
];

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
    ) : null;

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-xl md:text-2xl font-bold text-text">Credit Accounts</h1>
        <Select
          value={ownerTypeFilter || "ALL"}
          onValueChange={(v) => {
            setOwnerTypeFilter(v === "ALL" ? "" : v);
            setPage(0);
          }}
        >
          <SelectTrigger className="w-auto px-3 py-2 border bg-muted/50">
            <SelectValue placeholder="All Types" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Types</SelectItem>
            <SelectItem value="CLINIC">Clinic</SelectItem>
            <SelectItem value="HEALER">Healer</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {isLoading && <p className="text-text-light">Loading accounts…</p>}
      {isError && <p className="text-red-500">Failed to load accounts.</p>}

      {data && (
        <>
          {/* Desktop table */}
          <div className="hidden md:block">
            <DataTable<AccountRow>
              columns={accountColumns}
              data={data.content}
              enableSorting={false}
              emptyMessage="No accounts found."
              onRowClick={(row) =>
                goToTx(row.original.owner_type, row.original.owner_id)
              }
            />
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-3">
            {data.content.map((acc: AccountRow) => (
              <div key={acc.id} onClick={() => goToTx(acc.owner_type, acc.owner_id)}
                className="border border-border rounded-xl p-4 cursor-pointer active:bg-muted/60 transition-colors">
                <div className="flex items-start justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-text text-sm">{acc.owner_type}</span>
                    <TierBadge tier={acc.plan_tier} />
                  </div>
                  <ChevronRight className="w-4 h-4 text-text-light shrink-0" />
                </div>
                <p className={cn("truncate mb-3", acc.owner_name ? "text-sm font-medium text-text" : "font-mono text-xs text-text-light")}>
                  {acc.owner_name ?? acc.owner_id}
                </p>
                <div className="grid grid-cols-3 gap-2 text-xs">
                  <div className="bg-muted/50 rounded-lg px-2 py-1.5 text-center">
                    <div className="text-text-light">Free</div>
                    <div className="font-semibold text-text">{acc.free_balance}</div>
                  </div>
                  <div className="bg-muted/50 rounded-lg px-2 py-1.5 text-center">
                    <div className="text-text-light">Paid</div>
                    <div className="font-semibold text-text">{acc.paid_balance}</div>
                  </div>
                  <div className="bg-muted/50 rounded-lg px-2 py-1.5 text-center">
                    <div className="text-text-light">PAYG</div>
                    <div className="font-semibold text-text">{acc.payg_enabled ? "On" : "Off"}</div>
                  </div>
                </div>
                <p className="text-xs text-text-light mt-2">
                  Plan ends: {formatPlanEnd(acc.plan_expires_at)}
                </p>
                <p className="text-xs text-text-light mt-1">{new Date(acc.created_at).toLocaleDateString()}</p>
              </div>
            ))}
          </div>

          <Pagination />
        </>
      )}
    </div>
  );
}
