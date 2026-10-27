import { createColumnHelper } from "@tanstack/react-table";
import { format, parseISO } from "date-fns";
import { CheckCircle } from "lucide-react";
import { DataTable } from "@/components/ui/data-table";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { DashboardPager } from "./DashboardPager";
import { useDebouncedSearch } from "../hooks/useDebouncedSearch";
import { usePendingReports } from "../hooks/usePendingReports";
import type { PendingReport } from "../types/workqueue";

interface Props {
  healerId: string;
}

export function PendingReportsTable({ healerId }: Props) {
  const { search, setSearch, q, page, setPage } = useDebouncedSearch();
  const { data, isLoading, isError, refetch } = usePendingReports(healerId, q, page);

  const columnHelper = createColumnHelper<PendingReport>();

  const columns = [
    columnHelper.accessor(
      (row) => `${row.patientFirstName} ${row.patientLastName}`,
      { id: "patient", header: "Patient" }
    ),
    columnHelper.accessor("closureDate", {
      header: "Closed On",
      cell: (info) => {
        const v = info.getValue();
        return v ? format(parseISO(v), "dd MMM yyyy") : "—";
      },
    }),
    columnHelper.display({
      id: "reportStatus",
      header: "Report Status",
      cell: () => <Badge variant="destructive">PDF Missing</Badge>,
    }),
  ];

  return (
    <div className="rounded-2xl shadow-lg shadow-primary/20 bg-card p-6">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <p className="text-sm font-semibold text-text">Pending Reports</p>
        <Input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search patient..."
          aria-label="Search pending reports by patient name"
          className="h-8 w-full sm:w-56"
        />
      </div>
      <DataTable<PendingReport>
        columns={columns}
        data={data?.content ?? []}
        isLoading={isLoading}
        isError={isError}
        refetch={refetch}
        emptyIcon={CheckCircle}
        emptyMessage={q ? "No reports match your search" : "All reports are up to date"}
      />
      {data && (
        <DashboardPager page={page} totalPages={data.totalPages} last={data.last} onPageChange={setPage} />
      )}
    </div>
  );
}
