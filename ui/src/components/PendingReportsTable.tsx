import { createColumnHelper } from "@tanstack/react-table";
import { format, parseISO } from "date-fns";
import { CheckCircle } from "lucide-react";
import { WorkQueueTable } from "./WorkQueueTable";
import { usePendingReports } from "../hooks/usePendingReports";
import type { PendingReport } from "../types/workqueue";

interface Props {
  healerId: string;
}

export function PendingReportsTable({ healerId }: Props) {
  const { data, isLoading, isError, refetch } = usePendingReports(healerId);

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
      cell: () => (
        <span className="bg-danger/10 text-danger rounded-full px-2 py-0.5 text-xs font-medium">
          PDF Missing
        </span>
      ),
    }),
  ];

  return (
    <div className="rounded-2xl shadow-lg shadow-primary/20 bg-card p-6">
      <p className="text-sm font-semibold text-text mb-4">Pending Reports</p>
      <WorkQueueTable<PendingReport>
        columns={columns}
        data={data ?? []}
        isLoading={isLoading}
        isError={isError}
        refetch={refetch}
        emptyIcon={CheckCircle}
        emptyMessage="All reports are up to date"
      />
    </div>
  );
}
