import { useNavigate } from "react-router-dom";
import { createColumnHelper } from "@tanstack/react-table";
import { format, parseISO } from "date-fns";
import { FolderOpen } from "lucide-react";
import { DataTable } from "@/components/ui/data-table";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { DashboardPager } from "./DashboardPager";
import { useDashboardCases } from "../hooks/useDashboardCases";
import { useDebouncedSearch } from "../hooks/useDebouncedSearch";
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
  const { search, setSearch, q, page, setPage } = useDebouncedSearch();

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
      {data && (
        <DashboardPager page={page} totalPages={data.totalPages} last={data.last} onPageChange={setPage} />
      )}
    </div>
  );
}
