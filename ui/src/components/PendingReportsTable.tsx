import { createColumnHelper } from "@tanstack/react-table";
import { format, parseISO } from "date-fns";
import { CheckCircle } from "lucide-react";
import { DataTable } from "@/components/ui/data-table";
import { Badge } from "@/components/ui/badge";
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
      cell: () => <Badge variant="destructive">PDF Missing</Badge>,
    }),
  ];

  return (
    <div className="rounded-2xl shadow-lg shadow-primary/20 bg-card p-6">
      <p className="text-sm font-semibold text-text mb-4">Pending Reports</p>
      <DataTable<PendingReport>
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
