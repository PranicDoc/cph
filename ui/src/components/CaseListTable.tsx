import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { createColumnHelper } from "@tanstack/react-table";
import { format, parseISO } from "date-fns";
import { ChevronLeft, ChevronRight, FolderOpen } from "lucide-react";
import { DataTable } from "@/components/ui/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useDashboardCases } from "../hooks/useDashboardCases";
import type { DashboardCase } from "../types/workqueue";

interface Props {
  healerId: string;
}

const columnHelper = createColumnHelper<DashboardCase>();

const columns = [
  columnHelper.accessor(
    (row) => `${row.patientFirstName} ${row.patientLastName}`,
    { id: "patient", header: "Patient" }
  ),
  columnHelper.accessor("status", {
    header: "Status",
    cell: (info) => (
      <Badge variant={info.getValue() === "DRAFT" ? "secondary" : "default"}>
        {info.getValue() === "DRAFT" ? "Draft" : "Active"}
      </Badge>
    ),
  }),
  columnHelper.accessor("lastSessionDate", {
    header: "Last Session",
    cell: (info) => {
      const v = info.getValue();
      return v ? format(parseISO(v), "dd MMM yyyy") : "—";
    },
  }),
];

/** Active and draft cases with a name search and paging; a row opens the case to continue it. */
export function CaseListTable({ healerId }: Props) {
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);

  // Wait for a pause in typing before querying, and start again from the first page.
  useEffect(() => {
    const timer = setTimeout(() => {
      setQ(search.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  const { data, isLoading, isError, refetch } = useDashboardCases(healerId, q, page);

  return (
    <div className="rounded-2xl shadow-lg shadow-primary/20 bg-card p-6">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <p className="text-sm font-semibold text-text">Active &amp; Draft Cases</p>
        <Input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search patient..."
          aria-label="Search cases by patient name"
          className="h-8 w-full sm:w-56"
        />
      </div>
      <DataTable<DashboardCase>
        columns={columns}
        data={data?.content ?? []}
        isLoading={isLoading}
        isError={isError}
        refetch={refetch}
        emptyIcon={FolderOpen}
        emptyMessage={q ? "No cases match your search" : "No active or draft cases"}
        onRowClick={(row) => navigate(`/session/${row.original.caseId}`)}
      />
      {data && data.totalPages > 1 && (
        <div className="mt-3 flex items-center justify-end gap-2 text-xs text-text-light">
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="Previous page"
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
            aria-label="Next page"
            disabled={data.last}
            onClick={() => setPage((p) => p + 1)}
          >
            <ChevronRight className="w-4 h-4" />
          </Button>
        </div>
      )}
    </div>
  );
}
